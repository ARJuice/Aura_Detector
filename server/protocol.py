from typing import List, Literal, Optional
import base64
from pydantic import BaseModel, PrivateAttr, field_validator, model_validator

# Constants
UNAUTHORIZED = "UNAUTHORIZED"
UNSUPPORTED_VERSION = "UNSUPPORTED_VERSION"
INVALID_MESSAGE = "INVALID_MESSAGE"
INVALID_FRAME = "INVALID_FRAME"
SERVER_UNAVAILABLE = "SERVER_UNAVAILABLE"
RATE_LIMITED = "RATE_LIMITED"
MAX_FRAME_LONG_EDGE = 640
MAX_JPEG_BYTES = 320 * 1024

def validate_version(v: int) -> int:
    if v != 1:
        raise ValueError("Unsupported version")
    return v

def validate_token(v: str) -> str:
    if not v:
        raise ValueError("Token cannot be empty")
    return v

def validate_frame_dimensions(width: int, height: int, jpeg_size: int):
    if width < 2 or height < 2:
        raise ValueError("Dimensions must be at least 2x2")
    if max(width, height) > MAX_FRAME_LONG_EDGE:
        raise ValueError(f"Longest frame edge exceeds {MAX_FRAME_LONG_EDGE}px")
    if jpeg_size > MAX_JPEG_BYTES:
        raise ValueError("JPEG size exceeds maximum allowed (320KB)")

class Hello(BaseModel):
    type: Literal["hello"] = "hello"
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
    type: Literal["hello_ack"] = "hello_ack"
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
    type: Literal["frame"] = "frame"
    version: int = 1
    frameId: int
    capturedAtMs: int
    width: int
    height: int
    jpeg: str
    _jpeg_bytes: bytes = PrivateAttr(default=b"")

    @field_validator("version")
    @classmethod
    def check_version(cls, v: int) -> int:
        return validate_version(v)

    @model_validator(mode="after")
    def check_dimensions(self):
        try:
            jpeg_bytes = base64.b64decode(self.jpeg, validate=True)
            validate_frame_dimensions(self.width, self.height, len(jpeg_bytes))
            self._jpeg_bytes = jpeg_bytes
        except Exception as e:
            raise ValueError(f"Invalid frame: {str(e)}")
        return self

    @property
    def jpeg_bytes(self) -> bytes:
        return self._jpeg_bytes

class FrameState(BaseModel):
    type: Literal["frame_state"] = "frame_state"
    version: int = 1
    frameId: int
    serverReceivedAtMs: int
    inferenceMs: float
    subjects: List[Subject]

class ErrorMsg(BaseModel):
    type: Literal["error"] = "error"
    version: int = 1
    code: str
    message: str
    recoverable: bool
    frameId: Optional[int] = None
