from google.protobuf import timestamp_pb2 as _timestamp_pb2
from google.protobuf import struct_pb2 as _struct_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ActionType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    CREATE: _ClassVar[ActionType]
    UPDATE: _ClassVar[ActionType]
    DELETE: _ClassVar[ActionType]
    NO_OP: _ClassVar[ActionType]
CREATE: ActionType
UPDATE: ActionType
DELETE: ActionType
NO_OP: ActionType

class BatchInfo(_message.Message):
    __slots__ = ("batch_id", "total_items", "current_item_number", "batch_name", "started_at", "source_reference")
    BATCH_ID_FIELD_NUMBER: _ClassVar[int]
    TOTAL_ITEMS_FIELD_NUMBER: _ClassVar[int]
    CURRENT_ITEM_NUMBER_FIELD_NUMBER: _ClassVar[int]
    BATCH_NAME_FIELD_NUMBER: _ClassVar[int]
    STARTED_AT_FIELD_NUMBER: _ClassVar[int]
    SOURCE_REFERENCE_FIELD_NUMBER: _ClassVar[int]
    batch_id: str
    total_items: int
    current_item_number: int
    batch_name: str
    started_at: _timestamp_pb2.Timestamp
    source_reference: str
    def __init__(self, batch_id: _Optional[str] = ..., total_items: _Optional[int] = ..., current_item_number: _Optional[int] = ..., batch_name: _Optional[str] = ..., started_at: _Optional[_Union[_timestamp_pb2.Timestamp, _Mapping]] = ..., source_reference: _Optional[str] = ...) -> None: ...

class Embedding(_message.Message):
    __slots__ = ("model_id", "vector")
    MODEL_ID_FIELD_NUMBER: _ClassVar[int]
    VECTOR_FIELD_NUMBER: _ClassVar[int]
    model_id: str
    vector: _containers.RepeatedScalarFieldContainer[float]
    def __init__(self, model_id: _Optional[str] = ..., vector: _Optional[_Iterable[float]] = ...) -> None: ...

class ChunkEmbedding(_message.Message):
    __slots__ = ("text_content", "vector", "chunk_id", "original_char_start_offset", "original_char_end_offset", "chunk_group_id", "chunk_config_id")
    TEXT_CONTENT_FIELD_NUMBER: _ClassVar[int]
    VECTOR_FIELD_NUMBER: _ClassVar[int]
    CHUNK_ID_FIELD_NUMBER: _ClassVar[int]
    ORIGINAL_CHAR_START_OFFSET_FIELD_NUMBER: _ClassVar[int]
    ORIGINAL_CHAR_END_OFFSET_FIELD_NUMBER: _ClassVar[int]
    CHUNK_GROUP_ID_FIELD_NUMBER: _ClassVar[int]
    CHUNK_CONFIG_ID_FIELD_NUMBER: _ClassVar[int]
    text_content: str
    vector: _containers.RepeatedScalarFieldContainer[float]
    chunk_id: str
    original_char_start_offset: int
    original_char_end_offset: int
    chunk_group_id: str
    chunk_config_id: str
    def __init__(self, text_content: _Optional[str] = ..., vector: _Optional[_Iterable[float]] = ..., chunk_id: _Optional[str] = ..., original_char_start_offset: _Optional[int] = ..., original_char_end_offset: _Optional[int] = ..., chunk_group_id: _Optional[str] = ..., chunk_config_id: _Optional[str] = ...) -> None: ...

class SemanticChunk(_message.Message):
    __slots__ = ("chunk_id", "chunk_number", "embedding_info", "metadata")
    class MetadataEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: _struct_pb2.Value
        def __init__(self, key: _Optional[str] = ..., value: _Optional[_Union[_struct_pb2.Value, _Mapping]] = ...) -> None: ...
    CHUNK_ID_FIELD_NUMBER: _ClassVar[int]
    CHUNK_NUMBER_FIELD_NUMBER: _ClassVar[int]
    EMBEDDING_INFO_FIELD_NUMBER: _ClassVar[int]
    METADATA_FIELD_NUMBER: _ClassVar[int]
    chunk_id: str
    chunk_number: int
    embedding_info: ChunkEmbedding
    metadata: _containers.MessageMap[str, _struct_pb2.Value]
    def __init__(self, chunk_id: _Optional[str] = ..., chunk_number: _Optional[int] = ..., embedding_info: _Optional[_Union[ChunkEmbedding, _Mapping]] = ..., metadata: _Optional[_Mapping[str, _struct_pb2.Value]] = ...) -> None: ...

class SemanticProcessingResult(_message.Message):
    __slots__ = ("result_id", "source_field_name", "chunk_config_id", "embedding_config_id", "result_set_name", "chunks", "metadata")
    class MetadataEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: _struct_pb2.Value
        def __init__(self, key: _Optional[str] = ..., value: _Optional[_Union[_struct_pb2.Value, _Mapping]] = ...) -> None: ...
    RESULT_ID_FIELD_NUMBER: _ClassVar[int]
    SOURCE_FIELD_NAME_FIELD_NUMBER: _ClassVar[int]
    CHUNK_CONFIG_ID_FIELD_NUMBER: _ClassVar[int]
    EMBEDDING_CONFIG_ID_FIELD_NUMBER: _ClassVar[int]
    RESULT_SET_NAME_FIELD_NUMBER: _ClassVar[int]
    CHUNKS_FIELD_NUMBER: _ClassVar[int]
    METADATA_FIELD_NUMBER: _ClassVar[int]
    result_id: str
    source_field_name: str
    chunk_config_id: str
    embedding_config_id: str
    result_set_name: str
    chunks: _containers.RepeatedCompositeFieldContainer[SemanticChunk]
    metadata: _containers.MessageMap[str, _struct_pb2.Value]
    def __init__(self, result_id: _Optional[str] = ..., source_field_name: _Optional[str] = ..., chunk_config_id: _Optional[str] = ..., embedding_config_id: _Optional[str] = ..., result_set_name: _Optional[str] = ..., chunks: _Optional[_Iterable[_Union[SemanticChunk, _Mapping]]] = ..., metadata: _Optional[_Mapping[str, _struct_pb2.Value]] = ...) -> None: ...

class PipeDoc(_message.Message):
    __slots__ = ("id", "source_uri", "source_mime_type", "title", "body", "keywords", "document_type", "revision_id", "creation_date", "last_modified_date", "processed_date", "custom_data", "semantic_results", "named_embeddings", "blob", "metadata")
    class NamedEmbeddingsEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: Embedding
        def __init__(self, key: _Optional[str] = ..., value: _Optional[_Union[Embedding, _Mapping]] = ...) -> None: ...
    class MetadataEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    ID_FIELD_NUMBER: _ClassVar[int]
    SOURCE_URI_FIELD_NUMBER: _ClassVar[int]
    SOURCE_MIME_TYPE_FIELD_NUMBER: _ClassVar[int]
    TITLE_FIELD_NUMBER: _ClassVar[int]
    BODY_FIELD_NUMBER: _ClassVar[int]
    KEYWORDS_FIELD_NUMBER: _ClassVar[int]
    DOCUMENT_TYPE_FIELD_NUMBER: _ClassVar[int]
    REVISION_ID_FIELD_NUMBER: _ClassVar[int]
    CREATION_DATE_FIELD_NUMBER: _ClassVar[int]
    LAST_MODIFIED_DATE_FIELD_NUMBER: _ClassVar[int]
    PROCESSED_DATE_FIELD_NUMBER: _ClassVar[int]
    CUSTOM_DATA_FIELD_NUMBER: _ClassVar[int]
    SEMANTIC_RESULTS_FIELD_NUMBER: _ClassVar[int]
    NAMED_EMBEDDINGS_FIELD_NUMBER: _ClassVar[int]
    BLOB_FIELD_NUMBER: _ClassVar[int]
    METADATA_FIELD_NUMBER: _ClassVar[int]
    id: str
    source_uri: str
    source_mime_type: str
    title: str
    body: str
    keywords: _containers.RepeatedScalarFieldContainer[str]
    document_type: str
    revision_id: str
    creation_date: _timestamp_pb2.Timestamp
    last_modified_date: _timestamp_pb2.Timestamp
    processed_date: _timestamp_pb2.Timestamp
    custom_data: _struct_pb2.Struct
    semantic_results: _containers.RepeatedCompositeFieldContainer[SemanticProcessingResult]
    named_embeddings: _containers.MessageMap[str, Embedding]
    blob: Blob
    metadata: _containers.ScalarMap[str, str]
    def __init__(self, id: _Optional[str] = ..., source_uri: _Optional[str] = ..., source_mime_type: _Optional[str] = ..., title: _Optional[str] = ..., body: _Optional[str] = ..., keywords: _Optional[_Iterable[str]] = ..., document_type: _Optional[str] = ..., revision_id: _Optional[str] = ..., creation_date: _Optional[_Union[_timestamp_pb2.Timestamp, _Mapping]] = ..., last_modified_date: _Optional[_Union[_timestamp_pb2.Timestamp, _Mapping]] = ..., processed_date: _Optional[_Union[_timestamp_pb2.Timestamp, _Mapping]] = ..., custom_data: _Optional[_Union[_struct_pb2.Struct, _Mapping]] = ..., semantic_results: _Optional[_Iterable[_Union[SemanticProcessingResult, _Mapping]]] = ..., named_embeddings: _Optional[_Mapping[str, Embedding]] = ..., blob: _Optional[_Union[Blob, _Mapping]] = ..., metadata: _Optional[_Mapping[str, str]] = ...) -> None: ...

class Blob(_message.Message):
    __slots__ = ("blob_id", "data", "mime_type", "filename", "encoding", "metadata")
    class MetadataEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    BLOB_ID_FIELD_NUMBER: _ClassVar[int]
    DATA_FIELD_NUMBER: _ClassVar[int]
    MIME_TYPE_FIELD_NUMBER: _ClassVar[int]
    FILENAME_FIELD_NUMBER: _ClassVar[int]
    ENCODING_FIELD_NUMBER: _ClassVar[int]
    METADATA_FIELD_NUMBER: _ClassVar[int]
    blob_id: str
    data: bytes
    mime_type: str
    filename: str
    encoding: str
    metadata: _containers.ScalarMap[str, str]
    def __init__(self, blob_id: _Optional[str] = ..., data: _Optional[bytes] = ..., mime_type: _Optional[str] = ..., filename: _Optional[str] = ..., encoding: _Optional[str] = ..., metadata: _Optional[_Mapping[str, str]] = ...) -> None: ...

class FailedStepInputState(_message.Message):
    __slots__ = ("doc_state", "blob_state", "custom_config_struct", "config_params")
    class ConfigParamsEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    DOC_STATE_FIELD_NUMBER: _ClassVar[int]
    BLOB_STATE_FIELD_NUMBER: _ClassVar[int]
    CUSTOM_CONFIG_STRUCT_FIELD_NUMBER: _ClassVar[int]
    CONFIG_PARAMS_FIELD_NUMBER: _ClassVar[int]
    doc_state: PipeDoc
    blob_state: Blob
    custom_config_struct: _struct_pb2.Struct
    config_params: _containers.ScalarMap[str, str]
    def __init__(self, doc_state: _Optional[_Union[PipeDoc, _Mapping]] = ..., blob_state: _Optional[_Union[Blob, _Mapping]] = ..., custom_config_struct: _Optional[_Union[_struct_pb2.Struct, _Mapping]] = ..., config_params: _Optional[_Mapping[str, str]] = ...) -> None: ...

class ErrorData(_message.Message):
    __slots__ = ("error_message", "error_code", "technical_details", "originating_step_name", "attempted_target_step_name", "input_state_at_failure", "timestamp")
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    ERROR_CODE_FIELD_NUMBER: _ClassVar[int]
    TECHNICAL_DETAILS_FIELD_NUMBER: _ClassVar[int]
    ORIGINATING_STEP_NAME_FIELD_NUMBER: _ClassVar[int]
    ATTEMPTED_TARGET_STEP_NAME_FIELD_NUMBER: _ClassVar[int]
    INPUT_STATE_AT_FAILURE_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
    error_message: str
    error_code: str
    technical_details: str
    originating_step_name: str
    attempted_target_step_name: str
    input_state_at_failure: FailedStepInputState
    timestamp: _timestamp_pb2.Timestamp
    def __init__(self, error_message: _Optional[str] = ..., error_code: _Optional[str] = ..., technical_details: _Optional[str] = ..., originating_step_name: _Optional[str] = ..., attempted_target_step_name: _Optional[str] = ..., input_state_at_failure: _Optional[_Union[FailedStepInputState, _Mapping]] = ..., timestamp: _Optional[_Union[_timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class StepExecutionRecord(_message.Message):
    __slots__ = ("hop_number", "step_name", "service_instance_id", "start_time", "end_time", "status", "processor_logs", "error_info", "attempted_target_step_name")
    HOP_NUMBER_FIELD_NUMBER: _ClassVar[int]
    STEP_NAME_FIELD_NUMBER: _ClassVar[int]
    SERVICE_INSTANCE_ID_FIELD_NUMBER: _ClassVar[int]
    START_TIME_FIELD_NUMBER: _ClassVar[int]
    END_TIME_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    PROCESSOR_LOGS_FIELD_NUMBER: _ClassVar[int]
    ERROR_INFO_FIELD_NUMBER: _ClassVar[int]
    ATTEMPTED_TARGET_STEP_NAME_FIELD_NUMBER: _ClassVar[int]
    hop_number: int
    step_name: str
    service_instance_id: str
    start_time: _timestamp_pb2.Timestamp
    end_time: _timestamp_pb2.Timestamp
    status: str
    processor_logs: _containers.RepeatedScalarFieldContainer[str]
    error_info: ErrorData
    attempted_target_step_name: str
    def __init__(self, hop_number: _Optional[int] = ..., step_name: _Optional[str] = ..., service_instance_id: _Optional[str] = ..., start_time: _Optional[_Union[_timestamp_pb2.Timestamp, _Mapping]] = ..., end_time: _Optional[_Union[_timestamp_pb2.Timestamp, _Mapping]] = ..., status: _Optional[str] = ..., processor_logs: _Optional[_Iterable[str]] = ..., error_info: _Optional[_Union[ErrorData, _Mapping]] = ..., attempted_target_step_name: _Optional[str] = ...) -> None: ...

class PipeStream(_message.Message):
    __slots__ = ("stream_id", "document", "current_pipeline_name", "target_step_name", "current_hop_number", "history", "stream_error_data", "context_params", "action_type")
    class ContextParamsEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    STREAM_ID_FIELD_NUMBER: _ClassVar[int]
    DOCUMENT_FIELD_NUMBER: _ClassVar[int]
    CURRENT_PIPELINE_NAME_FIELD_NUMBER: _ClassVar[int]
    TARGET_STEP_NAME_FIELD_NUMBER: _ClassVar[int]
    CURRENT_HOP_NUMBER_FIELD_NUMBER: _ClassVar[int]
    HISTORY_FIELD_NUMBER: _ClassVar[int]
    STREAM_ERROR_DATA_FIELD_NUMBER: _ClassVar[int]
    CONTEXT_PARAMS_FIELD_NUMBER: _ClassVar[int]
    ACTION_TYPE_FIELD_NUMBER: _ClassVar[int]
    stream_id: str
    document: PipeDoc
    current_pipeline_name: str
    target_step_name: str
    current_hop_number: int
    history: _containers.RepeatedCompositeFieldContainer[StepExecutionRecord]
    stream_error_data: ErrorData
    context_params: _containers.ScalarMap[str, str]
    action_type: ActionType
    def __init__(self, stream_id: _Optional[str] = ..., document: _Optional[_Union[PipeDoc, _Mapping]] = ..., current_pipeline_name: _Optional[str] = ..., target_step_name: _Optional[str] = ..., current_hop_number: _Optional[int] = ..., history: _Optional[_Iterable[_Union[StepExecutionRecord, _Mapping]]] = ..., stream_error_data: _Optional[_Union[ErrorData, _Mapping]] = ..., context_params: _Optional[_Mapping[str, str]] = ..., action_type: _Optional[_Union[ActionType, str]] = ...) -> None: ...
