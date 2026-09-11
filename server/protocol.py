from typing import List, Optional
import base64
from pydantic import BaseModel, field_validator, model_validator

# Constants
UNAUTHORIZED = "UNAUTHORIZED"
UNSUPPORTED_VERSION = "UNSUPPORTED_VERSION"
INVALID_MESSAGE = "INVALID_MESSAGE"
INVALID_FRAME = "INVALID_FRAME"
SERVER_UNAVAILABLE = "SERVER_UNAVAILABLE"
RATE_LIMITED = "RATE_LIMITED"

def validate_version(v: int) -> int:
    if v != 1:
        raise ValueError("Unsupported version")
    return v

def validate_token(v: str) -> str:
    if not v:
        raise ValueError("Token cannot be empty")
    return v

def validate_frame_dimensions(width: int, height: int, jpeg_size: int):
    if width > 1920 or height > 1080:
        raise ValueError("Dimensions exceed maximum allowed (1920x1080)")
    if jpeg_size > 2 * 1024 * 1024:
        raise ValueError("JPEG size exceeds maximum allowed (2MB)")

class Hello(BaseModel):
    type: str = "hello"
    version: int
    token: str
    clientId: str

    @field_validator("version")
    @classmethod
    def check_version(cls, v: int) -> int:
        return validate_version(v)

    @field_validator("token")
    @classmethod
    def check_token(cls, v: str) -> str:
        return validate_token(v)

class HelloAck(BaseModel):
    type: str = "hello_ack"
    version: int = 1
    sessionId: str
    maxSubjects: int = 6
    serverTimeMs: int

class Profile(BaseModel):
    band: str
    min: str
    max: str
    palette: str

class Subject(BaseModel):
    id: int
    confidence: float
    box: List[float]
    contour: Optional[List[List[float]]] = None
    profile: Optional[Profile] = None

class Frame(BaseModel):
    type: str = "frame"
    version: int = 1
    frameId: int
    capturedAtMs: int
    width: int
    height: int
    jpeg: str

    @field_validator("version")
    @classmethod
    def check_version(cls, v: int) -> int:
        return validate_version(v)

    @model_validator(mode="after")
    def check_dimensions(self):
        try:
            jpeg_bytes = base64.b64decode(self.jpeg)
            validate_frame_dimensions(self.width, self.height, len(jpeg_bytes))
        except Exception as e:
            raise ValueError(f"Invalid frame: {str(e)}")
        return self

class FrameState(BaseModel):
    type: str = "frame_state"
    version: int = 1
    frameId: int
    serverReceivedAtMs: int
    inferenceMs: float
    subjects: List[Subject]

class ErrorMsg(BaseModel):
    type: str = "error"
    version: int = 1
    code: str
    message: str
    recoverable: bool
