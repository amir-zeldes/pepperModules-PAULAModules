/**
 * Copyright 2009 Humboldt University of Berlin, INRIA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */
package de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula.tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Hashtable;

import de.hu_berlin.german.korpling.saltnpepper.pepper.testSuite.moduleTests.util.FileComparator;
import de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula.Salt2PAULAMapper;
import de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula.exceptions.PAULAExporterException;
import de.hu_berlin.german.korpling.saltnpepper.salt.SaltFactory;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sCorpusStructure.SDocument;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SElementId;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltSample.SaltSample;

import junit.framework.TestCase;
import org.eclipse.emf.common.util.URI;
import org.xml.sax.InputSource;

 
 
public class Salt2PAULAMapperTest extends TestCase implements FilenameFilter{
	//TODO @Mario  move these files to ./src/test/resources/PAULAExporter and make these pathes to relative ones.
	
	
	private Salt2PAULAMapper fixture = null;
	private SaltSample saltSample = null;
	
	String resourcePath = (new File("src"+File.separator+"test"+File.separator+"resources"+File.separator).getAbsolutePath());
	
	
	String outputDirectory1 = resourcePath+File.separator+"SampleExport1"+File.separator;
	String outputDirectory2 = resourcePath+File.separator+"SampleExport2"+File.separator;
	
	public boolean accept( File f, String s )
	  {
	    return s.toLowerCase().endsWith( ".xml" ) 
	    	 & s.toLowerCase().indexOf("anno")==-1 ;
			
		
	  }

	
	public Salt2PAULAMapper getFixture() {
		return this.fixture;
	}

	public void setFixture(Salt2PAULAMapper fixture) {
		this.fixture = fixture;
	}
	
	public void setSaltSample(SaltSample saltSample){
		this.saltSample = saltSample;
	}
	
	@Override	
	public void setUp(){
		this.setFixture(new Salt2PAULAMapper());
		this.setSaltSample(new SaltSample());
		if (! new File(resourcePath).exists()){
			new File(resourcePath).mkdir();
			System.out.println("Creating Resource Path :"+ resourcePath);
		}
	}
	
	public void testMapCorpusStructure(){
		try {
		this.getFixture().mapCorpusStructure(null, null);
		fail("Null corpus Graph");
		} catch (PAULAExporterException e){
			
		}	
		
	}
	
	public void testMapSDocumentStructure() throws ClassNotFoundException{
		/*
		 * testing with null reference to Document Path and SDocument
		 */
		try {
			this.getFixture().mapSDocumentStructure(null, null);
			fail("Document Path and SDocument are not referenced");
		} catch (PAULAExporterException e){
				
		}	
		/*
		 * testing with null reference to Document Path
		 */
		try {
			this.getFixture().mapSDocumentStructure(SaltFactory.eINSTANCE.createSDocument(), null);
			fail("There is no reference to Document Path");
		} catch (PAULAExporterException e){
			
		}
		/*
		 * testing with null reference to SDocument
		 */
		try {
			this.getFixture().mapSDocumentStructure(null, URI.createURI(outputDirectory1));
			fail("There is no reference to SDocument");
		} catch (PAULAExporterException e){
			
		}

		/*
		 * testing with salt sample graph. Export twice and compare
		 */
		Hashtable<SElementId, URI> documentPaths1 = 
			this.getFixture().mapCorpusStructure(saltSample.getCorpus(), URI.createURI(outputDirectory1));
		Hashtable<SElementId, URI> documentPaths2 =
			this.getFixture().mapCorpusStructure(saltSample.getCorpus(), URI.createURI(outputDirectory2));
		// XML-file comparision
		for (SDocument sDocument : saltSample.getCorpus().getSDocuments()){
			this.getFixture().mapSDocumentStructure(sDocument, documentPaths1.get(sDocument.getSElementId()));
		}
		for (SDocument sDocument : saltSample.getCorpus().getSDocuments()){
			this.getFixture().mapSDocumentStructure(sDocument, documentPaths2.get(sDocument.getSElementId()));
		}
		for (SDocument sDocument : saltSample.getCorpus().getSDocuments()){
			this.compareDocuments(documentPaths1.get(sDocument.getSElementId()),documentPaths2.get(sDocument.getSElementId()));
		}
		this.cleanUp();
	}


	private void cleanUp() {
		File resourceDir = new File(resourcePath);
		deleteDirectory(new File(outputDirectory1));
		deleteDirectory(new File(outputDirectory2));
		
	}

	/**
	 * Deletes the directory with all contained directories/files
	 * @param fileToDelete
	 */
	private boolean deleteDirectory(File fileToDelete) {
		System.out.println("Deleting "+fileToDelete.getAbsolutePath());
		if (fileToDelete.isDirectory()) {
	        String[] directoryContent = fileToDelete.list();
	        for (int i=0; i < directoryContent.length; i++) {
	            if (! (deleteDirectory(new File(fileToDelete, directoryContent[i])))) {
	                return false;
	            }
	        }
	    }

	    return fileToDelete.delete();
	}


	/**
	 * Method for compating xml-documents. <br/>
	 * This method checks whether input and output files 
	 * are identical which should <br/>
	 * be the case since the 
	 * test works with SaltSample. 
	 * 
	 * @param uri input path
	 * @param uri2 output path
	 */
	private void compareDocuments(URI uri, URI uri2) {
		File fileToCheck = null;
		InputSource gold = null;
		InputSource toCheck = null;
		FileComparator fileComparator = new FileComparator();
		for (File in : new File(uri.toFileString()).listFiles(this)){
			fileToCheck = new File(uri2.toFileString()+File.separator+in.getName());
			try {
				toCheck = new InputSource(new FileInputStream(fileToCheck));
				gold = new InputSource(new FileInputStream(in));
				
				System.out.print("File "+in.getAbsolutePath()+" and "+ fileToCheck.getAbsolutePath()+" are");
				if (fileComparator.compareFiles(in, fileToCheck)){
					System.out.println(" equal!");
				} else {
					System.out.println(" not equal!");
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
	
}
