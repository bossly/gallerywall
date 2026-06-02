import argparse
import os
import random
import numpy as np
import tensorflow as tf
from tensorflow.keras import layers
from PIL import Image

# ----------------------------------------------------
# 1. DIFFUSION MODEL ARCHITECTURE
# ----------------------------------------------------

def make_diffusion_model(img_size=32, T=5, embedding_dim=64):
    """
    Lightweight Conditional U-Net for Denoising Diffusion.
    """
    x_t_input = layers.Input(shape=(img_size, img_size, 4), name="x_t")
    t_input = layers.Input(shape=(1,), dtype=tf.int32, name="t")
    
    # Timestep Embedding
    t_emb = layers.Embedding(T, embedding_dim)(t_input)
    t_emb = layers.Reshape((embedding_dim,))(t_emb)
    t_emb = layers.Dense(128)(t_emb)
    t_emb = layers.ReLU()(t_emb)
    
    # Down 1
    d1 = layers.Conv2D(32, kernel_size=3, padding='same')(x_t_input)
    d1 = layers.GroupNormalization(groups=8)(d1)
    d1 = layers.ReLU()(d1)
    
    cond_d1 = layers.Dense(32)(t_emb)
    cond_d1 = layers.Reshape((1, 1, 32))(cond_d1)
    d1 = layers.Add()([d1, cond_d1])
    
    # Down 2
    d2 = layers.Conv2D(64, kernel_size=4, strides=2, padding='same')(d1)
    d2 = layers.GroupNormalization(groups=8)(d2)
    d2 = layers.ReLU()(d2)
    
    cond_d2 = layers.Dense(64)(t_emb)
    cond_d2 = layers.Reshape((1, 1, 64))(cond_d2)
    d2 = layers.Add()([d2, cond_d2])
    
    # Down 3
    d3 = layers.Conv2D(128, kernel_size=4, strides=2, padding='same')(d2)
    d3 = layers.GroupNormalization(groups=8)(d3)
    d3 = layers.ReLU()(d3)
    
    # Bottleneck
    b = layers.Conv2D(128, kernel_size=3, padding='same')(d3)
    b = layers.GroupNormalization(groups=8)(b)
    b = layers.ReLU()(b)
    
    # Up 1
    u1 = layers.Conv2DTranspose(64, kernel_size=4, strides=2, padding='same')(b)
    u1 = layers.Concatenate()([u1, d2])
    u1 = layers.Conv2D(64, kernel_size=3, padding='same')(u1)
    u1 = layers.GroupNormalization(groups=8)(u1)
    u1 = layers.ReLU()(u1)
    
    # Up 2
    u2 = layers.Conv2DTranspose(32, kernel_size=4, strides=2, padding='same')(u1)
    u2 = layers.Concatenate()([u2, d1])
    u2 = layers.Conv2D(32, kernel_size=3, padding='same')(u2)
    u2 = layers.GroupNormalization(groups=8)(u2)
    u2 = layers.ReLU()(u2)
    
    # Output to predict noise
    out = layers.Conv2D(4, kernel_size=3, padding='same', activation=None, name="output")(u2)
    
    model = tf.keras.Model(inputs=[x_t_input, t_input], outputs=out)
    return model


# ----------------------------------------------------
# 2. DATASET LOADER
# ----------------------------------------------------

class CustomPixelArtDataset:
    """
    Loads pixel-art images dynamically and scales them to [-1, 1].
    """
    def __init__(self, root_dir, img_size):
        self.root_dir = root_dir
        self.img_size = img_size
        self.image_paths = []
        
        # Traverse and list all PNG, JPG, JPEG, WEBP images
        for root, _, files in os.walk(root_dir):
            for f in files:
                if f.lower().endswith(('.png', '.jpg', '.jpeg', '.webp')):
                    self.image_paths.append(os.path.join(root, f))
                    
        print(f"Loaded dataset: {len(self.image_paths)} images from {root_dir}.")

    def load_and_preprocess(self, path):
        """Loads an image, resizes, and scales pixels to [-1, 1]"""
        img = Image.open(path).convert('RGBA')
        img = img.resize((self.img_size, self.img_size))
        img_arr = (np.array(img, dtype=np.float32) / 127.5) - 1.0
        return img_arr


def create_tf_dataset(root_dir, img_size, batch_size):
    """Wraps CustomPixelArtDataset as a highly optimized tf.data.Dataset pipeline"""
    dataset = CustomPixelArtDataset(root_dir, img_size)
    
    def generator_func():
        for path in dataset.image_paths:
            try:
                img_arr = dataset.load_and_preprocess(path)
                yield img_arr
            except Exception:
                continue
            
    tf_ds = tf.data.Dataset.from_generator(
        generator_func,
        output_signature=tf.TensorSpec(shape=(img_size, img_size, 4), dtype=tf.float32)
    )
    tf_ds = tf_ds.shuffle(buffer_size=max(100, len(dataset.image_paths)))
    tf_ds = tf_ds.batch(batch_size, drop_remainder=True)
    tf_ds = tf_ds.prefetch(tf.data.AUTOTUNE)
    return tf_ds


# ----------------------------------------------------
# 3. DDPM TRAINING SCHEDULER & STEP
# ----------------------------------------------------

class DDPMScheduler:
    def __init__(self, T=5):
        self.T = T
        # Linear beta schedule
        self.betas = np.linspace(0.1, 0.9, T, dtype=np.float32)
        self.alphas = 1.0 - self.betas
        self.alphas_cumprod = np.cumprod(self.alphas)
        
        self.sqrt_alphas_cumprod = np.sqrt(self.alphas_cumprod)
        self.sqrt_one_minus_alphas_cumprod = np.sqrt(1.0 - self.alphas_cumprod)
        
    def add_noise(self, x_0, t, noise):
        """Noises clean samples x_0 to timestep t: x_t = sqrt_alpha_cumprod * x_0 + sqrt_one_minus * noise"""
        batch_size = tf.shape(x_0)[0]
        
        # Reshape schedule constants to match batch layout [batch, 1, 1, 1]
        sqrt_alpha = tf.gather(self.sqrt_alphas_cumprod, t)
        sqrt_alpha = tf.reshape(sqrt_alpha, [batch_size, 1, 1, 1])
        
        sqrt_one_minus = tf.gather(self.sqrt_one_minus_alphas_cumprod, t)
        sqrt_one_minus = tf.reshape(sqrt_one_minus, [batch_size, 1, 1, 1])
        
        x_t = sqrt_alpha * x_0 + sqrt_one_minus * noise
        return x_t


@tf.function
def train_step(model, opt, x_0, scheduler):
    batch_size = tf.shape(x_0)[0]
    
    # 1. Sample a random timestep t for each image in batch
    t = tf.random.uniform([batch_size], minval=0, maxval=scheduler.T, dtype=tf.int32)
    
    # 2. Sample noise N(0, I)
    noise = tf.random.normal(tf.shape(x_0))
    
    # 3. Add noise to clean image
    x_t = scheduler.add_noise(x_0, t, noise)
    
    # 4. Predict the noise using the U-Net model and compute MSE loss
    with tf.GradientTape() as tape:
        noise_pred = model([x_t, t], training=True)
        loss = tf.reduce_mean(tf.square(noise - noise_pred))
        
    gradients = tape.gradient(loss, model.trainable_variables)
    opt.apply_gradients(zip(gradients, model.trainable_variables))
    
    return loss


def main():
    parser = argparse.ArgumentParser(description="Train DDPM on Pixel Art sprites and export to TFLite")
    parser.add_argument("--data_dir", type=str, required=True, help="Path to training images dataset directory")
    parser.add_argument("--output_path", type=str, default="pixel_art_model.tflite", help="Export path for finished .tflite file")
    parser.add_argument("--epochs", type=int, default=100, help="Number of training epochs")
    parser.add_argument("--batch_size", type=int, default=64, help="Batch training size")
    parser.add_argument("--size", type=int, default=32, choices=[32, 64], help="Output resolution (32x32 or 64x64)")
    parser.add_argument("--lr", type=float, default=0.0002, help="Learning rate for Adam optimizer")
    args = parser.parse_args()

    # Pre-configure GPU growth if accessible
    gpus = tf.config.list_physical_devices('GPU')
    for gpu in gpus:
        try:
            tf.config.experimental.set_memory_growth(gpu, True)
        except Exception:
            pass

    # Create dataset pipeline
    dataloader = create_tf_dataset(args.data_dir, args.size, args.batch_size)

    # Initialize DDPM scheduler and U-Net Model
    scheduler = DDPMScheduler(T=5)
    model = make_diffusion_model(img_size=args.size)

    opt = tf.keras.optimizers.Adam(learning_rate=args.lr)

    print(f"Starting Diffusion training loop: {args.epochs} epochs | Size: {args.size}x{args.size} | Batch Size: {args.batch_size}")
    
    for epoch in range(args.epochs):
        epoch_losses = []
        for x_0 in dataloader:
            loss = train_step(model, opt, x_0, scheduler)
            epoch_losses.append(loss.numpy())
            
        avg_loss = np.mean(epoch_losses) if len(epoch_losses) > 0 else 0.0
        print(f"Epoch [{epoch+1}/{args.epochs}] | Average MSE Loss: {avg_loss:.6f}")

    # Export to optimized mobile TFLite format
    print("\nTraining completed! Compiling to TensorFlow Lite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()

    output_dir = os.path.dirname(args.output_path)
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)

    with open(args.output_path, "wb") as f:
        f.write(tflite_model)

    print(f"SUCCESS: Trained diffusion model successfully exported to: {args.output_path}")


if __name__ == "__main__":
    main()
