import os

# The legacy un-namespaced "xlm-roberta-base" repo (downloaded by MentalHealthClassifier.__init__)
# returns 404 on the Xet download path. Plain HTTP downloads work. Must run before huggingface_hub loads.
os.environ.setdefault("HF_HUB_DISABLE_XET", "1")
