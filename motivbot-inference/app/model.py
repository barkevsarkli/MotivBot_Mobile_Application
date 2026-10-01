"""Model definition and raw inference, mirroring the reference notebook."""

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from huggingface_hub import PyTorchModelHubMixin
from transformers import AutoModel, AutoTokenizer

from app import config
from app.scoring import metadata_vector

device = torch.device("cpu")


# Copied verbatim from the notebook (cell 3). Layer names must match the state dict.
class MentalHealthClassifier(nn.Module, PyTorchModelHubMixin):
    def __init__(self, config=None):
        super().__init__()
        self.xlmr  = AutoModel.from_pretrained('xlm-roberta-base')
        self.drop  = nn.Dropout(p=0.3)
        self.fusion = nn.Sequential(
            nn.Linear(768 + 4, 512),
            nn.LayerNorm(512),
            nn.GELU(),
            nn.Dropout(0.2),
        )
        self.heads_3 = nn.ModuleList([nn.Linear(512, 3) for _ in range(5)])

    def forward(self, ids, mask, struct):
        out   = self.xlmr(input_ids=ids, attention_mask=mask)
        cls   = out.last_hidden_state[:, 0, :]
        fused = torch.cat([cls, struct], dim=1)
        fused = self.fusion(self.drop(fused))
        return [h(fused) for h in self.heads_3]


class Predictor:
    """Holds the loaded model and tokenizer. Load once; reuse for every request."""

    def __init__(self, model_id: str = config.MODEL_ID):
        self.model_id = model_id
        self.tokenizer = AutoTokenizer.from_pretrained(model_id)
        self.model = MentalHealthClassifier.from_pretrained(model_id).to(device)
        self.model.eval()

    def predict_raw(self, text: str, age: float, sex: float, rses: float, phq: float) -> dict[str, list[float]]:
        """Returns {head: [p_no, p_low, p_high]} in config.HEADS order (notebook cell 7)."""
        metadata = np.array(metadata_vector(age, sex, rses, phq), dtype=np.float32)
        struct_t = torch.tensor(metadata).unsqueeze(0).to(device)

        inputs = self.tokenizer(text, return_tensors="pt", truncation=True,
                                max_length=config.MAX_LEN, padding='max_length').to(device)

        with torch.no_grad():
            outputs = self.model(inputs['input_ids'], inputs['attention_mask'], struct_t)

        result = {}
        for idx, head in enumerate(config.HEADS):
            scaled_logits = outputs[idx] / config.TEMPERATURE
            probs = F.softmax(scaled_logits, dim=1).squeeze(0).cpu().numpy()
            result[head] = [float(p) for p in probs]
        return result
