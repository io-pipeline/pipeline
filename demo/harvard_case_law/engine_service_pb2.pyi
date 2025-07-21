import pipeline_core_types_pb2 as _pipeline_core_types_pb2
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ProcessStatus(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    UNKNOWN: _ClassVar[ProcessStatus]
    ACCEPTED: _ClassVar[ProcessStatus]
    ERROR: _ClassVar[ProcessStatus]
UNKNOWN: ProcessStatus
ACCEPTED: ProcessStatus
ERROR: ProcessStatus

class PipeStreamResponse(_message.Message):
    __slots__ = ("stream_id", "status", "message", "request_id", "timestamp")
    STREAM_ID_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    REQUEST_ID_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
    stream_id: str
    status: ProcessStatus
    message: str
    request_id: str
    timestamp: int
    def __init__(self, stream_id: _Optional[str] = ..., status: _Optional[_Union[ProcessStatus, str]] = ..., message: _Optional[str] = ..., request_id: _Optional[str] = ..., timestamp: _Optional[int] = ...) -> None: ...
