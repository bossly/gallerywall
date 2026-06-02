import tensorflow as tf
from tensorflow.keras import layers
import os

def make_generator_model(num_classes=10, embedding_dim=50):
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

def main():
    print("Initializing PixelArtGenerator...")
    generator = make_generator_model()
    
    # Initialize weights with some randomized noise to produce visual variety even prior to training
    for layer in generator.layers:
        if isinstance(layer, layers.Conv2DTranspose):
            layer.set_weights([tf.random.normal(layer.weights[0].shape, 0.0, 0.02).numpy()])
        elif isinstance(layer, layers.BatchNormalization):
            gamma = tf.random.normal(layer.weights[0].shape, 1.0, 0.02).numpy()
            beta = tf.zeros(layer.weights[1].shape).numpy()
            mean = layer.weights[2].numpy()
            variance = layer.weights[3].numpy()
            layer.set_weights([gamma, beta, mean, variance])

    print("Converting Keras generator model to TensorFlow Lite (.tflite)...")
    converter = tf.lite.TFLiteConverter.from_keras_model(generator)
    tflite_model = converter.convert()
    
    output_dir = "app/src/main/assets"
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, "pixel_art_model.tflite")
    
    with open(output_path, "wb") as f:
        f.write(tflite_model)

    # Export default companion class style mapping JSON
    mapping_path = output_path.replace(".tflite", ".json")
    print(f"Saving default class style mapping JSON to: {mapping_path}")
    import json
    default_mapping = {
        "forest": 0, "green": 0, "tree": 0, "nature": 0,
        "cyber": 1, "neon": 1, "synth": 1, "future": 1,
        "space": 2, "star": 2, "galaxy": 2, "night": 2,
        "castle": 3, "dungeon": 3, "stone": 3, "retro": 3,
        "desert": 4, "sand": 4, "gold": 4, "sun": 4,
        "ocean": 5, "water": 5,
        "snow": 6, "ice": 6, "winter": 6, "cold": 6,
        "lava": 7, "fire": 7, "magma": 7, "red": 7,
        "candy": 8, "pink": 8, "sweet": 8, "cute": 8
    }
    with open(mapping_path, "w") as f:
        json.dump(default_mapping, f, indent=2)
        
    print("SUCCESS: Default mobile model pixel_art_model.tflite successfully created!")

if __name__ == "__main__":
    main()
