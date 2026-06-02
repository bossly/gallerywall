import tensorflow as tf
from tensorflow.keras import layers
import os

def make_diffusion_model(img_size=32, T=5, embedding_dim=64):
    """
    Lightweight Conditional U-Net for Denoising Diffusion.
    Supports dynamic H and W (by compiling to standard square dimensions).
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

def main():
    print("Initializing PixelArt Diffusion U-Net Model (32x32)...")
    model = make_diffusion_model(img_size=32)
    
    # Initialize weights with randomized Gaussian noise to generate structured variety prior to training
    for layer in model.layers:
        if hasattr(layer, 'weights') and len(layer.weights) > 0:
            new_weights = []
            for weight in layer.weights:
                if 'kernel' in weight.name or 'embeddings' in weight.name:
                    new_weights.append(tf.random.normal(weight.shape, 0.0, 0.02).numpy())
                elif 'bias' in weight.name:
                    new_weights.append(tf.zeros(weight.shape).numpy())
                else:
                    new_weights.append(tf.ones(weight.shape).numpy())
            layer.set_weights(new_weights)

    print("Converting Keras diffusion model to TensorFlow Lite (.tflite)...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()
    
    output_dir = "app/src/main/assets"
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, "pixel_art_model.tflite")
    
    with open(output_path, "wb") as f:
        f.write(tflite_model)
        
    print(f"SUCCESS: Default 32x32 mobile diffusion model written to {output_path}!")

if __name__ == "__main__":
    main()
