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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import static org.gradle.testkit.runner.TaskOutcome.*;
import static org.hamcrest.CoreMatchers.containsString;

public class InterlisRepositoryCreatorPluginTest {
    @Rule public final TemporaryFolder testProjectDir = new TemporaryFolder();
    private File buildFile;

    @Before
    public void setup() throws IOException {
        buildFile = testProjectDir.newFile("build.gradle");
        
        File srcDir = new File("src/test/data/models");
        File trgDir = new File(testProjectDir.getRoot().getAbsolutePath());

        FileUtils.copyDirectoryToDirectory(srcDir, trgDir);
    }

    @Test
    public void testInterlisRepositoryCreator() throws IOException {
        String buildFileContent = "plugins {\n" + 
                                  "     id 'ch.so.agi.interlis-repository-creator'\n" + 
                                  "}\n\n" +  
                                  "import ch.so.agi.tasks.InterlisRepositoryCreator;\n" +
                                  "" +
                                  "task createIliModelsXml(type: InterlisRepositoryCreator) {" +
                                  "     modelsDir = file('models') \n" +
                                  "     dataFile = 'ilimodels.xml' \n" +
                                  "}";
        
        writeFile(buildFile, buildFileContent);

        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments("createIliModelsXml")
            .withPluginClasspath()
            .build();

        assertEquals(SUCCESS, result.task(":createIliModelsXml").getOutcome());
        
        String resultString = new String(Files.readAllBytes(Paths.get(testProjectDir.getRoot().getAbsolutePath()+FileSystems.getDefault().getSeparator()+"ilimodels.xml")), StandardCharsets.UTF_8);
        
        assertThat(resultString, containsString("<Name>SO_MOpublic_20180221</Name>"));
        assertThat(resultString, containsString("<Name>DM01AVSO24LV95</Name>"));
        assertThat(resultString, containsString("<Name>SO_Nutzungsplanung_20171118</Name>"));        
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
