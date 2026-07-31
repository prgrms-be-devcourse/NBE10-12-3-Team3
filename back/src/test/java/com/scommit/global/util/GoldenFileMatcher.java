package com.scommit.global.util;

import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class  GoldenFileMatcher {

    public static void assertEqualsWithGolden(String relativeFilePath, String actualJson) throws Exception {
        Path path = Paths.get("src/test/resources", relativeFilePath);

        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, actualJson, StandardCharsets.UTF_8);
            System.out.println("새로운 골든 파일이 생성되었습니다 :  " + path.toAbsolutePath());
            return;
        }

        String expectedJson = Files.readString(path, StandardCharsets.UTF_8);
        
        // JSONAssert는 키 순서가 달라도 구조가 같으면 통과시켜주는 강력한 비교 도구입니다.
        JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.STRICT);
    }
}
