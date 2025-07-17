package io.pipeline.common.util;

import io.pipeline.data.util.ProtobufTestDataHelperTestBase;
import io.pipeline.data.util.proto.ProtobufTestDataHelper;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class ProtobufTestDataHelperIT extends ProtobufTestDataHelperTestBase {

    // Use a static instance to ensure thread safety test works
    private static ProtobufTestDataHelper protobufTestDataHelper;

    @Override
    protected ProtobufTestDataHelper getProtobufTestDataHelper() {
        if (protobufTestDataHelper == null) {
            synchronized (ProtobufTestDataHelperIT.class) {
                if (protobufTestDataHelper == null) {
                    protobufTestDataHelper = new ProtobufTestDataHelper();
                }
            }
        }
        return protobufTestDataHelper;
    }
}