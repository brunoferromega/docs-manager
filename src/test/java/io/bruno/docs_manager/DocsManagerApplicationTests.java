package io.bruno.docs_manager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestApplication.class)
class DocsManagerApplicationTests {

    @Test
    void contextLoads() {}
}
