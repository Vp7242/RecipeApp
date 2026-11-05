"""Train a food classifier on native Windows with your NVIDIA GPU (PyTorch) and export
food_model.onnx + labels.txt for the Android app.

Setup (Windows, PowerShell):
  py -3.11 -m venv venv ; .\\venv\\Scripts\\Activate.ps1
  pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
  pip install onnx pillow
  python train_food_model_windows.py

Default data: Food-101 (auto-downloads ~5 GB, 101 dishes).
Own data (e.g. Indian dishes): folders DATA_DIR/<class_name>/*.jpg  -> set DATA_DIR below.
"""
import os
import torch
import torch.nn as nn
import torchvision
from torchvision import transforms as T

DATA_DIR = None        # e.g. r"C:\data\indian_food"
IMG = 224
BATCH = 64             # lower to 32 if you get CUDA out-of-memory
EPOCHS_HEAD, EPOCHS_FINE = 2, 2
MEAN, STD = [0.485, 0.456, 0.406], [0.229, 0.224, 0.225]


class Export(nn.Module):
    """Takes raw RGB 0..255 (NCHW) exactly like the Android code feeds it; returns probabilities."""
    def __init__(self, net):
        super().__init__()
        self.net = net
        self.register_buffer("mean", torch.tensor(MEAN).view(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor(STD).view(1, 3, 1, 1))

    def forward(self, x):
        return torch.softmax(self.net((x / 255.0 - self.mean) / self.std), dim=1)


def main():
    dev = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print("device:", dev, torch.cuda.get_device_name(0) if dev.type == "cuda" else "(NO GPU - will be slow)")
    os.makedirs("out", exist_ok=True)

    train_tf = T.Compose([T.RandomResizedCrop(IMG, scale=(0.6, 1.0)), T.RandomHorizontalFlip(),
                          T.ColorJitter(0.2, 0.2, 0.2), T.ToTensor(), T.Normalize(MEAN, STD)])
    val_tf = T.Compose([T.Resize(256), T.CenterCrop(IMG), T.ToTensor(), T.Normalize(MEAN, STD)])

    if DATA_DIR:
        full_tr = torchvision.datasets.ImageFolder(DATA_DIR, train_tf)
        full_va = torchvision.datasets.ImageFolder(DATA_DIR, val_tf)
        g = torch.Generator().manual_seed(1)
        idx = torch.randperm(len(full_tr), generator=g).tolist()
        cut = int(0.85 * len(idx))
        train_ds = torch.utils.data.Subset(full_tr, idx[:cut])
        val_ds = torch.utils.data.Subset(full_va, idx[cut:])
        classes = full_tr.classes
    else:
        train_ds = torchvision.datasets.Food101("data", split="train", transform=train_tf, download=True)
        val_ds = torchvision.datasets.Food101("data", split="test", transform=val_tf, download=True)
        classes = train_ds.classes

    kw = dict(batch_size=BATCH, num_workers=8, pin_memory=True, persistent_workers=True)
    train_dl = torch.utils.data.DataLoader(train_ds, shuffle=True, **kw)
    val_dl = torch.utils.data.DataLoader(val_ds, **kw)
    print("classes:", len(classes))

    net = torchvision.models.mobilenet_v2(weights=torchvision.models.MobileNet_V2_Weights.IMAGENET1K_V1)
    net.classifier[1] = nn.Linear(net.last_channel, len(classes))
    net.to(dev)
    loss_fn = nn.CrossEntropyLoss(label_smoothing=0.1)
    scaler = torch.amp.GradScaler("cuda", enabled=dev.type == "cuda")

    def run_phase(epochs, lr, train_backbone):
        for p in net.features.parameters():
            p.requires_grad = train_backbone
        params = [p for p in net.parameters() if p.requires_grad]
        opt = torch.optim.AdamW(params, lr=lr, weight_decay=1e-4)
        sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, epochs * len(train_dl))
        for ep in range(epochs):
            net.train()
            for x, y in train_dl:
                x, y = x.to(dev, non_blocking=True), y.to(dev, non_blocking=True)
                with torch.autocast(dev.type, enabled=dev.type == "cuda"):
                    loss = loss_fn(net(x), y)
                opt.zero_grad(set_to_none=True)
                scaler.scale(loss).backward()
                scaler.step(opt); scaler.update(); sched.step()
            print(f"epoch done | loss {loss.item():.3f} | val top-1 acc {evaluate():.2%}")

    @torch.no_grad()
    def evaluate():
        net.eval(); ok = n = 0
        for x, y in val_dl:
            x, y = x.to(dev), y.to(dev)
            with torch.autocast(dev.type, enabled=dev.type == "cuda"):
                ok += (net(x).argmax(1) == y).sum().item()
            n += len(y)
        return ok / n

    run_phase(EPOCHS_HEAD, 1e-3, train_backbone=False)
    run_phase(EPOCHS_FINE, 1e-4, train_backbone=True)
    print(f"FINAL validation accuracy: {evaluate():.2%}")

    model = Export(net.float().cpu().eval()).eval()
    dummy = torch.zeros(1, 3, IMG, IMG)
    try:
        torch.onnx.export(model, dummy, "out/food_model.onnx", input_names=["input"], output_names=["probs"],
                          opset_version=17, dynamo=False)
    except TypeError:                       # older torch without the dynamo argument
        torch.onnx.export(model, dummy, "out/food_model.onnx", input_names=["input"], output_names=["probs"],
                          opset_version=17)
    open("out/labels.txt", "w", encoding="utf-8").write("\n".join(classes))
    print("saved out/food_model.onnx and out/labels.txt -> copy to app/src/main/assets/")


if __name__ == "__main__":      # required on Windows (DataLoader workers)
    main()
