from pydantic import BaseModel, Field
from typing import List, Optional
from enum import Enum
from decimal import Decimal
from datetime import datetime


class CourseCategory(str, Enum):
    BACKEND = "BACKEND"
    FRONTEND = "FRONTEND"
    DEVOPS = "DEVOPS"
    DATA_SCIENCE = "DATA_SCIENCE"
    MOBILE = "MOBILE"
    SECURITY = "SECURITY"
    DATABASE = "DATABASE"
    OTHER = "OTHER"


class CourseResponse(BaseModel):
    id: int
    title: str
    description: Optional[str] = None
    category: CourseCategory
    price: Decimal
    instructorId: int
    enrollmentCount: int
    status: str
    createdAt: Optional[datetime] = None


class EnrollmentHistoryResponse(BaseModel):
    userId: int
    receivedProductIds: List[int] = Field(default_factory=list)


class RecommendationItem(BaseModel):
    product: CourseResponse
    score: int = Field(ge=0, le=100)
    reason: str
    signals: List[str] = Field(default_factory=list)


class AiRecommendation(BaseModel):
    productId: int
    score: int = Field(ge=0, le=100)
    reason: str = Field(min_length=1, max_length=260)
    signals: List[str] = Field(default_factory=list, max_length=3)


class AiRecommendationPayload(BaseModel):
    recommendations: List[AiRecommendation] = Field(default_factory=list)


class RecommendResponse(BaseModel):
    userId: int
    recommendedCourses: List[CourseResponse]
    recommendations: List[RecommendationItem]
    basedOnCategory: Optional[CourseCategory] = None
    message: str
    analysisMode: str
    model: Optional[str] = None


class ApiResponse(BaseModel):
    success: bool
    message: str
    data: Optional[dict] = None
