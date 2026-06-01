import torch
import torch.nn as nn
from torch.utils.mobile_optimizer import optimize_for_mobile
import os

class PixelArtGenerator(nn.Module):
    def __init__(self, num_classes=10, embedding_dim=50):
        super().__init__()
        self.label_emb = nn.Embedding(num_classes, embedding_dim)
        
        # Generator mapping: Latent (100) + label (50) -> 3 x 64 x 64
        self.model = nn.Sequential(
            # Input: 150 x 1 x 1
            nn.ConvTranspose2d(150, 256, kernel_size=4, stride=1, padding=0, bias=False),
            nn.BatchNorm2d(256),
            nn.ReLU(True),
            # State: 256 x 4 x 4
            nn.ConvTranspose2d(256, 128, kernel_size=4, stride=2, padding=1, bias=False),
            nn.BatchNorm2d(128),
            nn.ReLU(True),
            # State: 128 x 8 x 8
            nn.ConvTranspose2d(128, 64, kernel_size=4, stride=2, padding=1, bias=False),
            nn.BatchNorm2d(64),
            nn.ReLU(True),
            # State: 64 x 16 x 16
            nn.ConvTranspose2d(64, 32, kernel_size=4, stride=2, padding=1, bias=False),
            nn.BatchNorm2d(32),
            nn.ReLU(True),
            # State: 32 x 32 x 32
            nn.ConvTranspose2d(32, 3, kernel_size=4, stride=2, padding=1, bias=False),
            nn.Tanh()
            # Output State: 3 x 64 x 64
        )

    def forward(self, noise, labels):
        # Safely clamp labels to prevent "index out of range" exceptions in nn.Embedding on mobile
        clamped_labels = torch.clamp(labels, 0, self.label_emb.num_embeddings - 1)
        c = self.label_emb(clamped_labels).unsqueeze(2).unsqueeze(3)
        x = torch.cat([noise.unsqueeze(2).unsqueeze(3), c], dim=1)
        return self.model(x)

def main():
    print("Initializing PixelArtGenerator...")
    generator = PixelArtGenerator()
    
    # Initialize weights with some randomized noise to produce visual variety even prior to training
    for m in generator.modules():
        if isinstance(m, nn.ConvTranspose2d):
            nn.init.normal_(m.weight.data, 0.0, 0.02)
        elif isinstance(m, nn.BatchNorm2d):
            nn.init.normal_(m.weight.data, 1.0, 0.02)
            nn.init.constant_(m.bias.data, 0)
            
    generator.eval()

    print("Tracing the model with input shapes: noise=[1, 100], label=[1]...")
    example_noise = torch.randn(1, 100)
    example_label = torch.tensor([0], dtype=torch.long)
    traced_module = torch.jit.trace(generator, (example_noise, example_label))

    print("Optimizing model for mobile runtime using optimize_for_mobile...")
    optimized_module = optimize_for_mobile(traced_module)

    output_dir = "app/src/main/assets"
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, "pixel_art_model.ptl")
    
    print(f"Saving optimized PyTorch Lite module to {output_path}...")
    optimized_module._save_for_lite_interpreter(output_path)
    
    print("SUCCESS: Default mobile model pixel_art_model.ptl successfully created!")

if __name__ == "__main__":
    main()
