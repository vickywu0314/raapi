package com.wenwen.testsupport;

/** 只读详情仪器的依赖边界；任何OCR业务调用都必须使测试失败。 */
public final class UnreadOcrBoundary {
    private UnreadOcrBoundary() { }
    public static org.springframework.beans.factory.FactoryBean<com.wenwen.ocr.OcrService> factory() {
        // FactoryBean产品不参加@Autowired字段填充，避免给只读mock装配真实OCR客户端/Mapper。
        return new org.springframework.beans.factory.FactoryBean<com.wenwen.ocr.OcrService>() {
            private final com.wenwen.ocr.OcrService boundary = create();
            public com.wenwen.ocr.OcrService getObject() { return boundary; }
            public Class<?> getObjectType() { return com.wenwen.ocr.OcrService.class; }
            public boolean isSingleton() { return true; }
        };
    }
    public static com.wenwen.ocr.OcrService create() {
        return org.mockito.Mockito.mock(com.wenwen.ocr.OcrService.class, invocation -> {
            if (invocation.getMethod().getDeclaringClass() == Object.class)
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            throw new AssertionError("只读详情夹具不得调用OCR:" + invocation.getMethod().getName());
        });
    }
}
