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

public class IlihubRepositoryCreatorTest {
    @Rule public final TemporaryFolder testProjectDir = new TemporaryFolder();
    private File buildFile;

    @Before
    public void setup() throws IOException {
        buildFile = testProjectDir.newFile("build.gradle");
        
        File srcDir = new File("src/test/data/ilihub");
        File trgDir = new File(testProjectDir.getRoot().getAbsolutePath());

        FileUtils.copyDirectoryToDirectory(srcDir, trgDir);
    }

    @Test
    public void testIlihubRepositoryCreator() throws IOException {
        String buildFileContent = 
"""
plugins {  
    id 'ch.so.agi.interlis-repository-creator' 
} 

import ch.so.agi.tasks.IlihubRepositoryCreator

task createIliDataXml(type: IlihubRepositoryCreator) {
    reposDir = file('ilihub')
    dataFile = 'ilidata.xml'
}
""";        
        writeFile(buildFile, buildFileContent);

        BufferedWriter log = new BufferedWriter(new OutputStreamWriter(System.out));
        
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments("createIliDataXml", "-i")
            //.withDebug(true)
            .forwardStdOutput(log)
            .withPluginClasspath()
            .build();

        assertEquals(SUCCESS, result.task(":createIliDataXml").getOutcome());
        
        String resultString = (Files.readString(Paths.get(testProjectDir.getRoot().getAbsolutePath()+FileSystems.getDefault().getSeparator()+"ilidata.xml"), StandardCharsets.UTF_8));
        
        assertThat(resultString, containsString("DatasetIdx16.DataIndex BID=\"b1\""));
        assertThat(resultString, containsString("<DatasetIdx16.DataIndex.DatasetMetadata TID=\"ed8b4d21-aec3-4acc-8479-b6aea5b54f9c\">"));
        assertThat(resultString, containsString("<path>afu_qrcat_usability-hub-configuration/layerstyle/qrcat_betrieb.qml</path>"));       
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
