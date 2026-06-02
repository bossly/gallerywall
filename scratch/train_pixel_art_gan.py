import argparse
import os
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
import random
import numpy as np
import tensorflow as tf
tf.get_logger().setLevel('ERROR')
from tensorflow.keras import layers
from PIL import Image

# ----------------------------------------------------
# 1. GAN MODEL ARCHITECTURE
# ----------------------------------------------------

def make_generator_model(num_classes=10, embedding_dim=50):
    """
    Generator Network (Conditional GAN)
    Maps a latent noise vector z (100) and class style embedding (50) to a 64x64 ARGB tile image.
    """
    noise_input = layers.Input(shape=(100,), name="noise")
    label_input = layers.Input(shape=(1,), dtype=tf.int32, name="label")
    
    # Safely clamp labels to prevent "index out of range" exceptions in Embedding
    clamped_labels = layers.Lambda(lambda x: tf.minimum(tf.maximum(x, 0), num_classes - 1))(label_input)
    
    # Embedding layer for label
    label_embedding = layers.Embedding(num_classes, embedding_dim)(clamped_labels)
    label_embedding = layers.Reshape((embedding_dim,))(label_embedding)
    
    # Concatenate noise and label embedding
    x = layers.Concatenate()([noise_input, label_embedding]) # Shape: (batch, 150)
    
    # Reshape to (1, 1, 150) for Conv2DTranspose
    x = layers.Reshape((1, 1, 150))(x)
    
    # Conv2DTranspose layers matching the original PyTorch Generator architecture (HWC format)
    # State: 1 x 1 x 150
    x = layers.Conv2DTranspose(256, kernel_size=4, strides=1, padding='valid', use_bias=False)(x)
    x = layers.BatchNormalization()(x)
    x = layers.ReLU()(x)
    # State: 4 x 4 x 256
    
    x = layers.Conv2DTranspose(128, kernel_size=4, strides=2, padding='same', use_bias=False)(x)
    x = layers.BatchNormalization()(x)
    x = layers.ReLU()(x)
    # State: 8 x 8 x 128
    
    x = layers.Conv2DTranspose(64, kernel_size=4, strides=2, padding='same', use_bias=False)(x)
    x = layers.BatchNormalization()(x)
    x = layers.ReLU()(x)
    # State: 16 x 16 x 64
    
    x = layers.Conv2DTranspose(32, kernel_size=4, strides=2, padding='same', use_bias=False)(x)
    x = layers.BatchNormalization()(x)
    x = layers.ReLU()(x)
    # State: 32 x 32 x 32
    
    x = layers.Conv2DTranspose(4, kernel_size=4, strides=2, padding='same', use_bias=False)(x)
    # State: 64 x 64 x 4
    
    out = layers.Activation('tanh', name="output")(x)
    
    model = tf.keras.Model(inputs=[noise_input, label_input], outputs=out)
    return model


def make_critic_model(num_classes=10, embedding_dim=50):
    """
    Discriminator Network (Wasserstein Critic)
    Evaluates authenticity of standard 64x64 generated textures conditioned on class embeddings.
    """
    img_input = layers.Input(shape=(64, 64, 4), name="image")
    label_input = layers.Input(shape=(1,), dtype=tf.int32, name="label")
    
    # Label embedding and projection
    label_embedding = layers.Embedding(num_classes, embedding_dim)(label_input)
    label_embedding = layers.Reshape((embedding_dim,))(label_embedding)
    label_proj = layers.Dense(64 * 64)(label_embedding)
    label_proj = layers.LeakyReLU(negative_slope=0.2)(label_proj)
    label_proj = layers.Reshape((64, 64, 1))(label_proj)
    
    # Concatenate image and label projection along channels
    x = layers.Concatenate(axis=-1)([img_input, label_proj]) # Shape: (64, 64, 4)
    
    # State: 64 x 64 x 4
    x = layers.Conv2D(64, kernel_size=4, strides=2, padding='same')(x)
    x = layers.LeakyReLU(negative_slope=0.2)(x)
    # State: 32 x 32 x 64
    
    x = layers.Conv2D(128, kernel_size=4, strides=2, padding='same')(x)
    x = layers.GroupNormalization(groups=-1)(x) # Instance Norm equivalent
    x = layers.LeakyReLU(negative_slope=0.2)(x)
    # State: 16 x 16 x 128
    
    x = layers.Conv2D(256, kernel_size=4, strides=2, padding='same')(x)
    x = layers.GroupNormalization(groups=-1)(x)
    x = layers.LeakyReLU(negative_slope=0.2)(x)
    # State: 8 x 8 x 256
    
    x = layers.Conv2D(512, kernel_size=4, strides=2, padding='same')(x)
    x = layers.GroupNormalization(groups=-1)(x)
    x = layers.LeakyReLU(negative_slope=0.2)(x)
    # State: 4 x 4 x 512
    
    x = layers.Conv2D(1, kernel_size=4, strides=1, padding='valid')(x)
    # Output represents raw score (no sigmoid for WGAN)
    out = layers.Reshape((1,))(x)
    
    model = tf.keras.Model(inputs=[img_input, label_input], outputs=out)
    return model


# ----------------------------------------------------
# 2. DATASET LOADER
# ----------------------------------------------------

class CustomPixelArtDataset:
    """
    Loads pixel-art images dynamically. Groups directories as style labels:
    E.g. data_dir/forest/img1.png -> label 0
         data_dir/cyber/img2.png  -> label 1
    """
    def __init__(self, root_dir):
        self.root_dir = root_dir
        self.image_paths = []
        self.labels = []
        
        # Sort subdirectories to maintain stable class mapping indices
        self.class_names = sorted([d for d in os.listdir(root_dir) if os.path.isdir(os.path.join(root_dir, d))])
        self.class_to_idx = {name: idx for idx, name in enumerate(self.class_names)}
        
        for class_name in self.class_names:
            class_idx = self.class_to_idx[class_name]
            class_dir = os.path.join(root_dir, class_name)
            for f in os.listdir(class_dir):
                if f.lower().endswith(('.png', '.jpg', '.jpeg', '.webp')):
                    self.image_paths.append(os.path.join(class_dir, f))
                    self.labels.append(class_idx)
                    
        print(f"Loaded dataset: {len(self.image_paths)} images across {len(self.class_names)} classes.")
        print(f"Class mapping: {self.class_to_idx}")

    def load_and_preprocess(self, path, label):
        """Loads an image, resizes, and scales pixels to [-1, 1]"""
        img = Image.open(path).convert('RGBA')
        img = img.resize((64, 64))
        img_arr = (np.array(img, dtype=np.float32) / 127.5) - 1.0
        return img_arr, label


def create_tf_dataset(root_dir, batch_size):
    """Wraps CustomPixelArtDataset as a highly optimized tf.data.Dataset pipeline"""
    dataset = CustomPixelArtDataset(root_dir)
    num_classes = len(dataset.class_names)
    
    def generator_func():
        for path, label in zip(dataset.image_paths, dataset.labels):
            try:
                img_arr, lbl = dataset.load_and_preprocess(path, label)
                yield img_arr, lbl
            except Exception as e:
                # Silently skip missing/corrupt files during dataset iteration so training runs uninterrupted
                continue
            
    tf_ds = tf.data.Dataset.from_generator(
        generator_func,
        output_signature=(
            tf.TensorSpec(shape=(64, 64, 4), dtype=tf.float32),
            tf.TensorSpec(shape=(), dtype=tf.int32)
        )
    )
    tf_ds = tf_ds.shuffle(buffer_size=len(dataset.image_paths))
    tf_ds = tf_ds.batch(batch_size, drop_remainder=True)
    tf_ds = tf_ds.prefetch(tf.data.AUTOTUNE)
    return tf_ds, num_classes, dataset


# ----------------------------------------------------
# 3. TRAINING ROUTINE
# ----------------------------------------------------

def compute_gradient_penalty(critic, real_samples, fake_samples, labels):
    """Calculates gradient penalty for WGAN-GP to enforce 1-Lipschitz constraint."""
    batch_size = tf.shape(real_samples)[0]
    alpha = tf.random.uniform([batch_size, 1, 1, 1], 0.0, 1.0)
    interpolated = real_samples * alpha + fake_samples * (1 - alpha)
    
    with tf.GradientTape() as gp_tape:
        gp_tape.watch(interpolated)
        pred = critic([interpolated, labels], training=True)
        
    grads = gp_tape.gradient(pred, [interpolated])[0]
    norm = tf.sqrt(tf.reduce_sum(tf.square(grads), axis=[1, 2, 3]))
    gradient_penalty = tf.reduce_mean((norm - 1.0) ** 2)
    return gradient_penalty


@tf.function
def train_step(generator, critic, opt_gen, opt_critic, real_imgs, labels):
    batch_size = tf.shape(real_imgs)[0]
    
    # ---------------- Train Critic (Discriminator) ----------------
    for _ in range(5):
        noise = tf.random.normal([batch_size, 100])
        with tf.GradientTape() as critic_tape:
            fake_imgs = generator([noise, labels], training=True)
            
            critic_real = critic([real_imgs, labels], training=True)
            critic_fake = critic([fake_imgs, labels], training=True)
            gp = compute_gradient_penalty(critic, real_imgs, fake_imgs, labels)
            
            # WGAN Loss: maximize critic(real) - critic(fake), add gradient penalty
            loss_critic = -(tf.reduce_mean(critic_real) - tf.reduce_mean(critic_fake)) + 10.0 * gp
            
        critic_gradients = critic_tape.gradient(loss_critic, critic.trainable_variables)
        opt_critic.apply_gradients(zip(critic_gradients, critic.trainable_variables))

    # ---------------- Train Generator ----------------
    noise = tf.random.normal([batch_size, 100])
    with tf.GradientTape() as gen_tape:
        fake_imgs = generator([noise, labels], training=True)
        gen_fake = critic([fake_imgs, labels], training=True)
        
        # WGAN Generator Loss: maximize critic(fake)
        loss_gen = -tf.reduce_mean(gen_fake)
        
    gen_gradients = gen_tape.gradient(loss_gen, generator.trainable_variables)
    opt_gen.apply_gradients(zip(gen_gradients, generator.trainable_variables))
    
    return loss_critic, loss_gen


def main():
    parser = argparse.ArgumentParser(description="Train Conditional GAN on 64x64 Pixel Art and export to Mobile TFLite format (.tflite)")
    parser.add_argument("--data_dir", type=str, required=True, help="Path to training images dataset directory")
    parser.add_argument("--output_path", type=str, default="pixel_art_model.tflite", help="Export path for finished .tflite file")
    parser.add_argument("--epochs", type=int, default=150, help="Number of training epochs")
    parser.add_argument("--batch_size", type=int, default=64, help="Batch training size")
    parser.add_argument("--lr", type=float, default=0.0002, help="Learning rate for Adam optimizer")
    args = parser.parse_args()

    # Create dataset pipeline
    dataloader, num_classes, dataset = create_tf_dataset(args.data_dir, args.batch_size)

    # Initialize networks
    generator = make_generator_model(num_classes=num_classes)
    critic = make_critic_model(num_classes=num_classes)

    # Optimizers
    opt_gen = tf.keras.optimizers.Adam(learning_rate=args.lr, beta_1=0.0, beta_2=0.9)
    opt_critic = tf.keras.optimizers.Adam(learning_rate=args.lr, beta_1=0.0, beta_2=0.9)

    # Training Loop
    for epoch in range(args.epochs):
        for real_imgs, labels in dataloader:
            loss_critic, loss_gen = train_step(generator, critic, opt_gen, opt_critic, real_imgs, labels)

        # Print metrics
        print(f"Epoch [{epoch+1}/{args.epochs}] | Loss Critic: {loss_critic.numpy():.4f} | Loss Gen: {loss_gen.numpy():.4f}")

    # ----------------------------------------------------
    # 4. EXPORT TO MOBILE TENSORFLOW LITE
    # ----------------------------------------------------
    print("\nTraining completed! Preparing model for TensorFlow Lite export...")
    converter = tf.lite.TFLiteConverter.from_keras_model(generator)
    tflite_model = converter.convert()

    print(f"Saving TensorFlow Lite module to: {args.output_path}")
    with open(args.output_path, "wb") as f:
        f.write(tflite_model)

    # Export companion class style mapping JSON
    mapping_path = args.output_path.replace(".tflite", ".json")
    print(f"Saving class style mapping JSON to: {mapping_path}")
    import json
    with open(mapping_path, "w") as f:
        json.dump(dataset.class_to_idx, f, indent=2)

    print("SUCCESS: Finished training and successfully wrote mobile model weights! Ready for Android assets integration.")


if __name__ == "__main__":
    main()
