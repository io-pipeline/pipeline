package io.pipeline.data.util;

import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class ProtobufTestDataHelperTest extends ProtobufTestDataHelperTestBase {

    @Inject
    ProtobufTestDataHelper protobufTestDataHelper;

    @Override
    protected ProtobufTestDataHelper getProtobufTestDataHelper() {
        return protobufTestDataHelper;
    }
}