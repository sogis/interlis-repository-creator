package ch.so.agi.tasks;

import org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Before;
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

public class ConfigDataRepositoryCreatorTest {
    @Rule public final TemporaryFolder testProjectDir = new TemporaryFolder();
    private File buildFile;

    @Before
    public void setup() throws IOException {
        buildFile = testProjectDir.newFile("build.gradle");
        
        File srcDir = new File("src/test/data/config");
        File dstDir = new File(testProjectDir.getRoot().getAbsolutePath());

        FileUtils.copyDirectoryToDirectory(srcDir, dstDir);
    }

    @Test
    public void configDataXml_Ok() throws IOException {
        String buildFileContent = 
"""
plugins {  
    id 'ch.so.agi.interlis-repository-creator' 
} 

import ch.so.agi.tasks.ConfigDataRepositoryCreator

task createConfigDataXml(type: ConfigDataRepositoryCreator) {
    configDir = file('config')
    dataFile = 'ilidata.xml'
    owner = 'mailto:foo@bar.ch'
}
""";
        
        writeFile(buildFile, buildFileContent);

        BufferedWriter log = new BufferedWriter(new OutputStreamWriter(System.out));
        
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments("createConfigDataXml", "-i")
            //.withDebug(true)
            .forwardStdOutput(log)
            .withPluginClasspath()
            .build();

        assertEquals(SUCCESS, result.task(":createConfigDataXml").getOutcome());
        
        String resultString = new String(Files.readAllBytes(Paths.get(testProjectDir.getRoot().getAbsolutePath()+FileSystems.getDefault().getSeparator()+"ilidata.xml")), StandardCharsets.UTF_8);
        
        assertThat(resultString, containsString("DatasetIdx16.DataIndex BID=\"b1\""));
        assertThat(resultString, containsString("<id>drainagen</id>"));
        assertThat(resultString, containsString("<id>drainagen-meta</id>"));
        assertThat(resultString, containsString("<value>http://codes.interlis.ch/type/metaconfig</value>"));       
        assertThat(resultString, containsString("<id>ipw_2020-meta</id>"));               
        assertThat(resultString, containsString("mailto:foo@bar.ch"));               
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
