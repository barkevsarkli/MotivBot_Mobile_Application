"""Request/response models for the platform-neutral /predict contract."""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, StrictInt

Level = Literal["No", "Low", "High"]


class PredictRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    text: str = Field(description="Free-text self description. Trimmed; must not be empty.")
    age: StrictInt = Field(description="Age in years, 10-100.")
    gender: Literal["male", "female", "unspecified"]
    phq9_items: list[StrictInt] = Field(description="PHQ-9 items 1-9, each 0-3. Attention check excluded.")
    rses_items: list[StrictInt] = Field(
        description="Rosenberg items 1-10 as raw app values (Strongly agree = 3 ... Strongly disagree = 0)."
    )
    language: str | None = Field(default=None, description="Informational only.")


class InputsUsed(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    age: int = Field(alias="Age")
    sex: float
    phq9: int = Field(alias="PHQ-9")
    rosenberg: int = Field(alias="Rosenberg.self-esteem")


class Probabilities(BaseModel):
    No: float
    Low: float
    High: float


class Dimension(BaseModel):
    key: str
    level: Level
    probabilities: Probabilities


class PredictResponse(BaseModel):
    model_config = ConfigDict(protected_namespaces=())

    model_id: str
    inputs_used: InputsUsed
    dimensions: list[Dimension]
    elevated_risk: bool


class HealthResponse(BaseModel):
    model_config = ConfigDict(protected_namespaces=())

    status: Literal["loading", "ready", "error"]
    model_id: str
