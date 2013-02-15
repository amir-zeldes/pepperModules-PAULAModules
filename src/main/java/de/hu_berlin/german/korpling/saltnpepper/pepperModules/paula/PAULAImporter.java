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
package de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula;

import java.io.File;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.osgi.service.log.LogService;

import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperExceptions.PepperFWException;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperExceptions.PepperModuleException;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.PepperImporter;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.PepperMapper;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.impl.PepperImporterImpl;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.impl.PepperMapperImpl;
import de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula.exceptions.PAULAImporterException;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.SaltCommonFactory;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sCorpusStructure.SCorpus;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sCorpusStructure.SCorpusDocumentRelation;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sCorpusStructure.SCorpusGraph;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sCorpusStructure.SCorpusRelation;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sCorpusStructure.SDocument;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SElementId;

/**
 * This importer reads a corpus in PAULA format and maps it to a SALT corpus. 
 * The mapping of each document is done in a separate thread. 
 * 
 * @author Florian Zipser
 * @version 1.0
 *
 */
@Component(name="PAULAImporterComponent", factory="PepperImporterComponentFactory")
@Service(value=PepperImporter.class)
public class PAULAImporter extends PepperImporterImpl implements PepperImporter
{
	public PAULAImporter()
	{
		super();
		
		//setting name of module
		this.name= "PAULAImporter";
		
		//set list of formats supported by this module
		this.addSupportedFormat("paula", "1.0", null);
		
		{//just for logging: to say, that the current module has been loaded
			if (this.getLogService()!= null)
				this.getLogService().log(LogService.LOG_DEBUG,this.getName()+" is created...");
		}//just for logging: to say, that the current module has been loaded
	}
	
//===================================== start: thread number
	/**
	 * Defines the number of processes which can maximal work in parallel for importing documents.
	 * Means the number of parallel imported documents. Default value is 5.
	 */
	private Integer numOfParallelDocuments= 5;
	/**
	 * Sets the number of processes which can maximal work in parallel for importing documents.
	 * Means the number of parallel imported documents.
	 * @param numOfParallelDocuments the numOfParallelDocuments to set
	 */
	public void setNumOfParallelDocuments(Integer numOfParallelDocuments) {
		this.numOfParallelDocuments = numOfParallelDocuments;
	}

	/**
	 * Returns the number of processes which can maximal work in parallel for importing documents.
	 * Means the number of parallel imported documents.
	 * @return the numOfParallelDocuments
	 */
	public Integer getNumOfParallelDocuments() {
		return numOfParallelDocuments;
	}	
	
	public static final String PROP_NUM_OF_PARALLEL_DOCUMENTS="paulaImporter.numOfParallelDocuments";
//===================================== start: thread number
	
// ========================== start: flagging for parallel running	
	/**
	 * If true, PAULAImporter imports documents in parallel.
	 */
	private Boolean RUN_IN_PARALLEL= true;
	/**
	 * @param rUN_IN_PARALLEL the rUN_IN_PARALLEL to set
	 */
	public void setRUN_IN_PARALLEL(Boolean rUN_IN_PARALLEL) {
		RUN_IN_PARALLEL = rUN_IN_PARALLEL;
	}

	/**
	 * @return the RUN_IN_PARALLEL
	 */
	public Boolean getRUN_IN_PARALLEL() {
		return RUN_IN_PARALLEL;
	}
	
	/**
	 * Identifier of properties which contains the maximal number of parallel processed documents. 
	 */
	public static final String PROP_RUN_IN_PARALLEL="paulaImporter.runInParallel";
// ========================== end: flagging for parallel running
	/**
	 * a property representation of a property file
	 */
	protected Properties props= null;
	
	/**
	 * Stores relation between documents and their resource 
	 */
	private Map<SElementId, URI> sDocumentResourceTable= null;
	
	private SCorpusGraph sCorpusGraph= null;
	
	/**
	 * This method is called by Pepper at the start of conversion process. 
	 * It shall create the structure the corpus to import. That means creating all necessary SCorpus, 
	 * SDocument and all Relation-objects between them. The path tp the corpus to import is given by
	 * this.getCorpusDefinition().getCorpusPath().
	 * @param an empty graph given by Pepper, which shall contains the corpus structure
	 */
	@Override
	public void importCorpusStructure(SCorpusGraph sCorpusGraph)
			throws PepperModuleException 
	{
		if (this.getCorpusDefinition().getCorpusPath()== null)
			throw new PAULAImporterException("Cannot import corpus-structure, because no corpus-path is given.");
		
		this.sDocumentResourceTable= new Hashtable<SElementId, URI>();	
		this.sCorpusGraph= sCorpusGraph;
		File corpusPath= new File(this.getCorpusDefinition().getCorpusPath().toFileString());
		this.extractCorpusStructureRec(corpusPath, corpusPath.getName(), null);
	}
// ========================== start: extract corpus-path	
	/**
	 * Stores the endings which are used for paula-files
	 */
	private String[] PAULA_FILE_ENDINGS= {"xml","paula"};
	
	/**
	 * Reads the file structure of the given corpus directory and translates it to a corpus-structure.
	 * @param corpusPath
	 * @param currentPath the string representation of path which will be used to create the SElementId
	 */
	private void extractCorpusStructureRec(File corpusPath, String currentPath, SCorpus parentSCorpus)
	{
		if (!corpusPath.exists())
			throw new PAULAImporterException("Cannot import corpus-structure, because the corpus-path does not exists: "+ corpusPath.getAbsolutePath());
		Boolean hasPAULAFiles= false;
		Boolean hasFolders= false;
		EList<File> subFolders= new BasicEList<File>();
		for (File currentFile: corpusPath.listFiles())
		{
			if (currentFile.isDirectory())
			{
				hasFolders= true;
				subFolders.add(currentFile);
			}
			else if(currentFile.isFile())
			{
				String currentEnding= null;
				String[] parts= currentFile.getName().split("[.]");
				if (parts.length>=2)
				{
					currentEnding= parts[parts.length-1];
				}
				for (String PAULAEnding: PAULA_FILE_ENDINGS)
				{
					if (currentEnding.equalsIgnoreCase(PAULAEnding))
					{
						hasPAULAFiles= true;
						break;
					}
				}
			}
		}
		if (	(!hasFolders) &&
				(!hasPAULAFiles))
		{//folder doesn't contain PAULA-files or other folders
			if (this.getLogService()!= null)
				this.getLogService().log(LogService.LOG_WARNING, "The folder '"+corpusPath.getAbsolutePath()+"' will not be imported, because it does not contain other folders or PAULA-documents.");
		}//folder doesn't contain PAULA-files or other folders
		else if (	(hasPAULAFiles) &&
					(!hasFolders))
		{//the current folder is a document
			if (parentSCorpus== null)
			{//create an artificial root corpus, because no one exists in data
				SElementId sCorpusId= SaltCommonFactory.eINSTANCE.createSElementId();
				sCorpusId.setSId(currentPath);
				parentSCorpus= SaltCommonFactory.eINSTANCE.createSCorpus();
				parentSCorpus.setSName(corpusPath.getName());
				parentSCorpus.setSElementId(sCorpusId);
				this.sCorpusGraph.addSNode(parentSCorpus);
				if (this.getLogService()!= null)
					this.getLogService().log(LogService.LOG_WARNING, "No root corpus exists in PAULA data (in '"+corpusPath.getAbsolutePath()+"'), an artificial one with the name '"+corpusPath.getName()+"' has been created.");
			}//create an artificial root corpus, because no one exists in data
			SElementId sDocumentId= SaltCommonFactory.eINSTANCE.createSElementId();
			sDocumentId.setSId(currentPath);
			String documentName= corpusPath.getName();
			SDocument sDocument= SaltCommonFactory.eINSTANCE.createSDocument();
			sDocument.setSName(documentName);
			sDocument.setSElementId(sDocumentId);
			this.sCorpusGraph.addSNode(sDocument);
			SCorpusDocumentRelation sCorpDocRel= SaltCommonFactory.eINSTANCE.createSCorpusDocumentRelation();
			sCorpDocRel.setSDocument(sDocument);
			sCorpDocRel.setSCorpus(parentSCorpus);
			this.sCorpusGraph.addSRelation(sCorpDocRel);
			
			URI sDocumentURI= URI.createFileURI(corpusPath.getAbsolutePath());
			this.sDocumentResourceTable.put(sDocumentId, sDocumentURI);
		}//the current folder is a document
		else if (hasFolders)
		{//the current folder is a corpus
			if(	(hasPAULAFiles) &&
				(hasFolders))
			{//folder does also contain PAULA-files and other folders --> ignore the files 
				if (this.getLogService()!= null)
					this.getLogService().log(LogService.LOG_WARNING, "The folder '"+corpusPath.getAbsolutePath()+"' also contains sub folders and PAULA-documents. This is not compatible to the PAULA-format. Therefore the PAULA-files will be ignored.");
			}//folder does also contain PAULA-files and other folders --> ignore the files
			
			SElementId sCorpusId= SaltCommonFactory.eINSTANCE.createSElementId();
			sCorpusId.setSId(currentPath);
			SCorpus sCorpus= SaltCommonFactory.eINSTANCE.createSCorpus();
			sCorpus.setSName(corpusPath.getName());
			sCorpus.setSElementId(sCorpusId);
			this.sCorpusGraph.addSNode(sCorpus);
			if (parentSCorpus!= null)
			{//if parent-corpus exists create a relation between parent and current	
				SCorpusRelation sCorpRel= SaltCommonFactory.eINSTANCE.createSCorpusRelation();
				sCorpRel.setSSuperCorpus(parentSCorpus);
				sCorpRel.setSSubCorpus(sCorpus);			
				this.sCorpusGraph.addSRelation(sCorpRel);
			}//if parent-corpus exists create a relation between parent and current
			if (subFolders!= null)
			{	
				for (File subFolder: subFolders)
				{
					StringBuilder newCorpusPath= new StringBuilder();
					newCorpusPath.append((currentPath== null)? subFolder.getName().trim() : currentPath+"/"+subFolder.getName().trim()); 
					this.extractCorpusStructureRec(subFolder, newCorpusPath.toString(), sCorpus);
				}
			}
		}//the current folder is a corpus
	}
// ========================== end: extract corpus-path
	
	@Override
	public void start() throws PepperModuleException
	{
		boolean isStart= true;
		SElementId sElementId= null;
		while ((isStart) || (sElementId!= null))
		{	
			isStart= false;
			sElementId= this.getPepperModuleController().get();
			if (sElementId== null)
				break;
			
			//call for using push-method
			this.start(sElementId);
		}	
		
		for (PepperMapperImpl mapper: this.getMappers())
		{
			try {
				mapper.join();
			} catch (InterruptedException e) {
				throw new PepperModuleException("An exception occured while waiting for thread to terminate '"+mapper.getName()+"'.", e);
			}
			//TODO: set UncoughtExceptionHandler and read it here
		}
		this.end();
	}
	
	/**
	 * A threadsafe list of all mapper objects
	 */
	private Collection<PepperMapperImpl> mappers= null;
	
	/**
	 * A lock for method {@link #getMappers()} to create a new mappers list.
	 */
	private Lock getMapperLock= new ReentrantLock(); 
	/**
	 * Returns a threadsafe list of all mappers that have been started.
	 * @return
	 */
	protected Collection<PepperMapperImpl> getMappers()
	{
		if (mappers== null)
		{
			getMapperLock.lock();
			try{
				mappers= new Vector<PepperMapperImpl>();
			}finally
			{getMapperLock.unlock();}
		}
		return(mappers);
	}
	
	/**
	 * This method is called by method start() of superclass PepperImporter, if the method was not overriden
	 * by the current class. If this is not the case, this method will be called for every document which has
	 * to be processed.
	 * @param sElementId the id value for the current document or corpus to process  
	 */
	@Override
	public void start(SElementId sElementId) throws PepperModuleException 
	{
		if (	(sElementId!= null) &&
				(sElementId.getSIdentifiableElement()!= null) &&
				((sElementId.getSIdentifiableElement() instanceof SDocument) ||
				((sElementId.getSIdentifiableElement() instanceof SCorpus))))
		{//only if given sElementId belongs to an object of type SDocument or SCorpus	
			if (sElementId.getSIdentifiableElement() instanceof SCorpus)
			{//mapping SCorpus	
			}//mapping SCorpus
			if (sElementId.getSIdentifiableElement() instanceof SDocument)
			{//mapping SDocument
				SDocument sDocument= (SDocument) sElementId.getSIdentifiableElement();
				MapperRunner mapperRunner= new MapperRunner();
				{//configure mapper and mapper runner
					PAULA2SaltMapper mapper= new PAULA2SaltMapper();
					mapperRunner.mapper= mapper;
					mapperRunner.sDocument= sDocument;
					mapper.setPAULA_FILE_ENDINGS(PAULA_FILE_ENDINGS);
					mapper.setSDocument(sDocument);
					mapper.setLogService(this.getLogService());
				}//configure mapper and mapper runner
				
				if (this.getRUN_IN_PARALLEL())
				{//run import in parallel	
					this.mapperRunners.add(mapperRunner);
					this.executorService.execute(mapperRunner);
				}//run import in parallel
				else 
				{//do not run import in parallel
					mapperRunner.start();
				}//do not run import in parallel
			}//mapping SDocument
		}//only if given sElementId belongs to an object of type SDocument or SCorpus
	}
	
	/**
	 * This class is a container for running PAULAMappings in parallel.
	 * @author Administrator
	 *
	 */
	private class MapperRunner implements java.lang.Runnable
	{
		public SDocument sDocument= null;
		PAULA2SaltMapper mapper= null;
		
		/**
		 * Lock to lock await and signal methods.
		 */
		protected Lock lock= new ReentrantLock();
		
		/**
		 * Flag wich says, if mapperRunner has started and finished
		 */
		private Boolean isFinished= false;
		
		/**
		 * If condition is achieved a new SDocument can be created.
		 */
		private Condition finishCondition=lock.newCondition();
		
		
		@Override
		public void run() 
		{
			start();
		}
		
		/**
		 * starts Mapping of PAULA data
		 */
		public void start()
		{
			if (mapper== null)
				throw new PAULAImporterException("BUG: Cannot start import, because the mapper is null.");
			if (sDocument== null)
				throw new PAULAImporterException("BUG: Cannot start import, because no SDocument object is given.");
			{//getting paula-document-path
				URI paulaDoc= sDocumentResourceTable.get(sDocument.getSElementId());
				if (paulaDoc== null)
					throw new PAULAImporterException("BUG: Cannot start import, no paula-document-path was found for SDocument '"+sDocument.getSElementId()+"'.");
				mapper.setResourceURI(paulaDoc);
			}//getting paula-document-path
			try 
			{
				mapper.mapSDocument();
				getPepperModuleController().put(this.sDocument.getSElementId());
			}catch (Exception e)
			{
				if (getLogService()!= null)
				{
					getLogService().log(LogService.LOG_WARNING, "Cannot import the SDocument '"+sDocument.getSName()+"'. The reason is: "+e);
				}
				getPepperModuleController().finish(this.sDocument.getSElementId());
			}
			//remove mapper
			mapper= null;
			this.lock.lock();
			this.isFinished= true;
			this.finishCondition.signal();
			this.lock.unlock();
		}
	}
}
