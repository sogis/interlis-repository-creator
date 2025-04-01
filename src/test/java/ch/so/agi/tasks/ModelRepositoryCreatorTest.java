package ch.so.agi.tasks;

import org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import static org.gradle.testkit.runner.TaskOutcome.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

public class ModelRepositoryCreatorTest {
    @Rule public final TemporaryFolder testProjectDir = new TemporaryFolder();
    private File buildFile;

    @Before
    public void setup() throws IOException {
        buildFile = testProjectDir.newFile("build.gradle");
        
        {
            File srcDir = new File("src/test/data/models");
            File dstDir = new File(testProjectDir.getRoot().getAbsolutePath());
            FileUtils.copyDirectoryToDirectory(srcDir, dstDir);            
        }
        {
            File srcDir = new File("src/test/data/models-ext");
            File dstDir = new File(testProjectDir.getRoot().getAbsolutePath());
            FileUtils.copyDirectoryToDirectory(srcDir, dstDir);            
        }
    }

    @Test
    public void testModelRepositoryCreator_V20() throws IOException {
        String buildFileContent = 
"""
plugins {  
    id 'ch.so.agi.interlis-repository-creator' 
} 

import ch.so.agi.tasks.ModelRepositoryCreator

task createIliModelsXml(type: ModelRepositoryCreator) {
    modelsDirectory = file('models')
    dataFile = file('ilimodels.xml')
    ignoredDirectories = "mirror"
}
""";
        
        writeFile(buildFile, buildFileContent);

        BufferedWriter log = new BufferedWriter(new OutputStreamWriter(System.out));
        
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments("createIliModelsXml", "-i")
            .withDebug(true)
            .forwardStdOutput(log)
            .withPluginClasspath()
            .build();
        
        assertEquals(SUCCESS, result.task(":createIliModelsXml").getOutcome());
        
        String resultString = new String(Files.readAllBytes(Paths.get(testProjectDir.getRoot().getAbsolutePath()+FileSystems.getDefault().getSeparator()+"ilimodels.xml")), StandardCharsets.UTF_8);
        
        assertThat(resultString, containsString("<IliRepository20.RepositoryIndex BID=\"b1\">"));
        assertThat(resultString, not(containsString("<Name>SO_MOpublic_20180221</Name>")));
        assertThat(resultString, containsString("<Name>DM01AVSO24LV95</Name>"));
        assertThat(resultString, containsString("<SchemaLanguage>ili2_4</SchemaLanguage>"));
        assertThat(resultString, not(containsString("GraphicCHLV95_V1")));
    }
        
    private void writeFile(File destination, String content) throws IOException {
        BufferedWriter output = null;
        try {
            output = new BufferedWriter(new FileWriter(destination));
            output.write(content);
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }
}
