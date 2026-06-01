import argparse
import os
import random
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
from torchvision import transforms
from torch.utils.mobile_optimizer import optimize_for_mobile
from PIL import Image

# ----------------------------------------------------
# 1. GAN MODEL ARCHITECTURE
# ----------------------------------------------------

class PixelArtGenerator(nn.Module):
    """
    Generator Network (Conditional GAN)
    Maps a latent noise vector z (100) and class style embedding (50) to a 64x64 ARGB tile image.
    """
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


class PixelArtDiscriminator(nn.Module):
    """
    Discriminator Network (Wasserstein Critic)
    Evaluates authenticity of standard 64x64 generated textures conditioned on class embeddings.
    """
    def __init__(self, num_classes=10, embedding_dim=50):
        super().__init__()
        self.label_emb = nn.Embedding(num_classes, embedding_dim)
        
        # Projection layer to merge label channel with image
        self.label_project = nn.Sequential(
            nn.Linear(embedding_dim, 64 * 64),
            nn.LeakyReLU(0.2, True)
        )
        
        self.model = nn.Sequential(
            # Input: 4 channels (RGB + Label projection) x 64 x 64
            nn.Conv2d(4, 64, kernel_size=4, stride=2, padding=1),
            nn.LeakyReLU(0.2, inplace=True),
            # State: 64 x 32 x 32
            nn.Conv2d(64, 128, kernel_size=4, stride=2, padding=1),
            nn.InstanceNorm2d(128, affine=True),
            nn.LeakyReLU(0.2, inplace=True),
            # State: 128 x 16 x 16
            nn.Conv2d(128, 256, kernel_size=4, stride=2, padding=1),
            nn.InstanceNorm2d(256, affine=True),
            nn.LeakyReLU(0.2, inplace=True),
            # State: 256 x 8 x 8
            nn.Conv2d(256, 512, kernel_size=4, stride=2, padding=1),
            nn.InstanceNorm2d(512, affine=True),
            nn.LeakyReLU(0.2, inplace=True),
            # State: 512 x 4 x 4
            nn.Conv2d(512, 1, kernel_size=4, stride=1, padding=0),
            # Output represents raw score (no sigmoid for WGAN)
        )

    def forward(self, img, labels):
        # img shape: [B, 3, 64, 64], labels shape: [B]
        c = self.label_emb(labels) # [B, 50]
        c_proj = self.label_project(c).view(-1, 1, 64, 64) # [B, 1, 64, 64]
        x = torch.cat([img, c_proj], dim=1) # [B, 4, 64, 64]
        return self.model(x).view(-1)


# ----------------------------------------------------
# 2. DATASET LOADER
# ----------------------------------------------------

class CustomPixelArtDataset(Dataset):
    """
    Loads pixel-art images dynamically. Groups directories as style labels:
    E.g. data_dir/forest/img1.png -> label 0
         data_dir/cyber/img2.png  -> label 1
    """
    def __init__(self, root_dir, transform=None):
        self.root_dir = root_dir
        self.transform = transform
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

    def __len__(self):
        return len(self.image_paths)

    def __getitem__(self, idx):
        img_path = self.image_paths[idx]
        image = Image.open(img_path).convert('RGB')
        label = self.labels[idx]
        
        if self.transform:
            image = self.transform(image)
            
        return image, label


# ----------------------------------------------------
# 3. TRAINING ROUTINE
# ----------------------------------------------------

def compute_gradient_penalty(critic, real_samples, fake_samples, labels, device):
    """Calculates gradient penalty for WGAN-GP to enforce 1-Lipschitz constraint."""
    alpha = torch.rand(real_samples.size(0), 1, 1, 1, device=device)
    interpolates = (alpha * real_samples + ((1 - alpha) * fake_samples)).requires_grad_(True)
    d_interpolates = critic(interpolates, labels)
    fake = torch.ones(real_samples.size(0), device=device)
    gradients = torch.autograd.grad(
        outputs=d_interpolates,
        inputs=interpolates,
        grad_outputs=fake,
        create_graph=True,
        retain_graph=True,
        only_inputs=True,
    )[0]
    gradients = gradients.view(gradients.size(0), -1)
    gradient_penalty = ((gradients.norm(2, dim=1) - 1) ** 2).mean()
    return gradient_penalty


def main():
    parser = argparse.ArgumentParser(description="Train Conditional GAN on 64x64 Pixel Art and export to Mobile format (.ptl)")
    parser.add_argument("--data_dir", type=str, required=True, help="Path to training images dataset directory")
    parser.add_argument("--output_path", type=str, default="pixel_art_model.ptl", help="Export path for finished .ptl file")
    parser.add_argument("--epochs", type=int, default=150, help="Number of training epochs")
    parser.add_argument("--batch_size", type=int, default=64, help="Batch training size")
    parser.add_argument("--lr", type=float, default=0.0002, help="Learning rate for Adam optimizer")
    parser.add_argument("--device", type=str, default="cuda" if torch.cuda.is_available() else "mps" if torch.backends.mps.is_available() else "cpu", help="Computation hardware device")
    args = parser.parse_args()

    print(f"Training WGAN-GP on device: {args.device}")

    # Transforms for 64x64 scaling and normalization
    transform = transforms.Compose([
        transforms.Resize((64, 64)),
        transforms.ToTensor(),
        transforms.Normalize((0.5, 0.5, 0.5), (0.5, 0.5, 0.5)) # Normalizes range to [-1, 1]
    ])

    dataset = CustomPixelArtDataset(args.data_dir, transform=transform)
    dataloader = DataLoader(dataset, batch_size=args.batch_size, shuffle=True, drop_last=True)

    num_classes = len(dataset.class_names)
    
    # Initialize networks
    generator = PixelArtGenerator(num_classes=num_classes).to(args.device)
    critic = PixelArtDiscriminator(num_classes=num_classes).to(args.device)

    # Optimizers
    opt_gen = optim.Adam(generator.parameters(), lr=args.lr, betas=(0.0, 0.9))
    opt_critic = optim.Adam(critic.parameters(), lr=args.lr, betas=(0.0, 0.9))

    # Training Loop
    for epoch in range(args.epochs):
        for batch_idx, (real_imgs, labels) in enumerate(dataloader):
            real_imgs = real_imgs.to(args.device)
            labels = labels.to(args.device)
            cur_batch_size = real_imgs.size(0)

            # ---------------- Train Critic (Discriminator) ----------------
            for _ in range(5): # Train critic more frequently for Wasserstein convergence
                noise = torch.randn(cur_batch_size, 100, device=args.device)
                fake_imgs = generator(noise, labels)
                
                critic_real = critic(real_imgs, labels)
                critic_fake = critic(fake_imgs.detach(), labels)
                gp = compute_gradient_penalty(critic, real_imgs, fake_imgs, labels, args.device)
                
                # WGAN Loss: maximize critic(real) - critic(fake), add gradient penalty
                loss_critic = -(torch.mean(critic_real) - torch.mean(critic_fake)) + 10 * gp
                
                critic.zero_grad()
                loss_critic.backward(retain_graph=True)
                opt_critic.step()

            # ---------------- Train Generator ----------------
            noise = torch.randn(cur_batch_size, 100, device=args.device)
            fake_imgs = generator(noise, labels)
            gen_fake = critic(fake_imgs, labels)
            
            # WGAN Generator Loss: maximize critic(fake)
            loss_gen = -torch.mean(gen_fake)

            generator.zero_grad()
            loss_gen.backward()
            opt_gen.step()

        # Print metrics
        print(f"Epoch [{epoch+1}/{args.epochs}] | Loss Critic: {loss_critic.item():.4f} | Loss Gen: {loss_gen.item():.4f}")

    # ----------------------------------------------------
    # 4. EXPORT TO MOBILE PYTORCH INTERPRETER
    # ----------------------------------------------------
    print("\nTraining completed! Preparing trace operation for Mobile export...")
    generator.eval().cpu()
    
    # Trace model inputs: noise=[1, 100], labels=[1]
    example_noise = torch.randn(1, 100)
    example_label = torch.tensor([0], dtype=torch.long)
    traced_model = torch.jit.trace(generator, (example_noise, example_label))

    # Optimize traced graph for mobile interpreters
    print("Running optimize_for_mobile on traced graph...")
    optimized_model = optimize_for_mobile(traced_model)

    print(f"Saving optimized PyTorch Lite module to: {args.output_path}")
    optimized_model._save_for_lite_interpreter(args.output_path)
    print("SUCCESS: Finished tracing and successfully wrote mobile model weights! Ready for Android assets integration.")


if __name__ == "__main__":
    main()
