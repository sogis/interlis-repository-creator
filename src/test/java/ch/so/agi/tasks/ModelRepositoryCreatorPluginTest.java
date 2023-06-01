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

public class ModelRepositoryCreatorPluginTest {
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
    public void testModelRepositoryCreator_V09() throws IOException {
        String buildFileContent = 
"""
plugins {  
    id 'ch.so.agi.interlis-repository-creator' 
} 

import ch.so.agi.tasks.ModelRepositoryCreator

task createIliModelsXml(type: ModelRepositoryCreator) {
    modelsDir = file('models')
    dataFile = 'ilimodels.xml'
    technicalContact = 'mailto:foo@bar.ch'
    ilismeta = true
}
""";
        
        writeFile(buildFile, buildFileContent);

        BufferedWriter log = new BufferedWriter(new OutputStreamWriter(System.out));
        
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments("createIliModelsXml", "-i")
            //.withDebug(true)
            .forwardStdOutput(log)
            .withPluginClasspath()
            .build();

        assertEquals(SUCCESS, result.task(":createIliModelsXml").getOutcome());
        
        String resultString = new String(Files.readAllBytes(Paths.get(testProjectDir.getRoot().getAbsolutePath()+FileSystems.getDefault().getSeparator()+"ilimodels.xml")), StandardCharsets.UTF_8);
        
        assertThat(resultString, containsString("<IliRepository09.RepositoryIndex BID=\"b1\">"));
        assertThat(resultString, not(containsString("<Name>SO_MOpublic_20180221</Name>")));
        assertThat(resultString, containsString("<Name>DM01AVSO24LV95</Name>"));
        assertThat(resultString, containsString("<Name>SO_Nutzungsplanung_20171118</Name>"));       
        assertThat(resultString, containsString("<Name>SO_AWJF_Waldpflege_Erfassung_20191112</Name>"));               
        assertThat(resultString, containsString("<Name>Base_f_LV95</Name>"));               
        assertThat(resultString, containsString("<Issuer>https://arp.so.ch</Issuer><technicalContact>mailto:agi@bd.so.ch</technicalContact>"));
        assertThat(resultString, containsString("<Issuer>https://agi.so.ch</Issuer><technicalContact>mailto:foo@bar.ch</technicalContact>"));
        assertThat(resultString, containsString("<Title>Ich bin auch ein Titel</Title><shortDescription>Ich bin die Beschreibung</shortDescription>"));        
        
        String ilismetaString = new String(Files.readAllBytes(Paths.get(testProjectDir.getRoot().getAbsolutePath(), "ilismeta", "SO_AGI_AV_GB_Administrative_Einteilungen_Publikation_20180822.xml")), StandardCharsets.UTF_8);
        assertThat(ilismetaString, containsString("</IlisMeta07.ModelData>"));
        assertThat(ilismetaString, containsString("<IlisMeta07.ModelData.Ili1TransferElement><Ili1TransferClass REF=\"SO_AGI_AV_GB_Administrative_Einteilungen_Publikation_20180822.Nachfuehrungskreise.Gemeinde\"></Ili1TransferClass><Ili1RefAttr REF=\"SO_AGI_AV_GB_Administrative_Einteilungen_Publikation_20180822.Nachfuehrungskreise.Gemeinde.UID\" ORDER_POS=\"16\"></Ili1RefAttr></IlisMeta07.ModelData.Ili1TransferElement>"));
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
    modelsDir = file('models')
    dataFile = 'ilimodels.xml'
    repoModelName = 'IliRepository20'
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
