import logging
from typing import List
from .protocol import Subject

logger = logging.getLogger(__name__)

class VisionPipeline:
    """
    Vision pipeline for processing frames and detecting subjects.
    """
    def __init__(self):
        self.model_ready = False
        self.model = None

    def load_model(self):
        """
        Load the object detection model.
        """
        logger.info("Model loading deferred to Step 4")
        self.model_ready = True

    def process_frame(self, jpeg_bytes: bytes, width: int, height: int) -> List[Subject]:
        """
        Process a single frame.
        """
        return []

    def warmup(self):
        """
        Warmup the model.
        """
        logger.info("Warmup complete")
