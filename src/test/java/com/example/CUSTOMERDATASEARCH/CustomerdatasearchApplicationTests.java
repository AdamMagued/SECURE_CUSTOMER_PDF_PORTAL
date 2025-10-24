package com.example.CUSTOMERDATASEARCH;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "war.auto.generate=false",
    "war.storage.dir=${java.io.tmpdir}/test_wars"
})
class CustomerdatasearchApplicationTests {

    @Test
    void contextLoads() {
        // This test verifies that the Spring application context loads successfully
        // with all beans configured properly
    }

}