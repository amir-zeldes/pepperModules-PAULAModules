package de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula.tests;

import junit.framework.TestCase;
import de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula.PAULAExporter;
import de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula.exceptions.PAULAExporterException;

public class PAULAExporterTest extends TestCase
{
	private PAULAExporter fixture= null;
	
	public PAULAExporter getFixture() {
		return fixture;
	}

	public void setFixture(PAULAExporter fixture) {
		this.fixture = fixture;
	} 
	
	public void setUp()
	{
		this.setFixture(new PAULAExporter());
//		this.setSaltSample(new SaltSample());
	}
	
//	private SaltSample saltSample = null;
//	public void setSaltSample(SaltSample saltSample){
//		this.saltSample = saltSample;
//	}
	
//	String resourcePath = "file://"+(new File("_TMP"+File.separator+"test"+File.separator).getAbsolutePath());
//	String outputDirectory1 = resourcePath+File.separator+"SampleExport1"+File.separator;
//	String outputDirectory2 = resourcePath+File.separator+"SampleExport2"+File.separator;
	
	public void testMapCorpusStructure(){
		try {
		this.getFixture().mapCorpusStructure(null, null);
		fail("Null corpus Graph");
		} catch (PAULAExporterException e){
			
		}	
		
	}
}
