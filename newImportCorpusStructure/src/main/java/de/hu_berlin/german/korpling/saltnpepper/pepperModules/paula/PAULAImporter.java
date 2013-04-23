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
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.log.LogService;

import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperExceptions.PepperFWException;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperExceptions.PepperModuleException;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.MAPPING_RESULT;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.PepperImporter;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.PepperMapper;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.PepperMapperConnector;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.impl.PepperImporterImpl;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.impl.PepperMapperConnectorImpl;
import de.hu_berlin.german.korpling.saltnpepper.pepper.pepperModules.impl.PepperMapperImpl;
import de.hu_berlin.german.korpling.saltnpepper.pepperModules.paula.exceptions.PAULAImporterException;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sCorpusStructure.SCorpus;
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
//@Service(value=PepperImporter.class)
public class PAULAImporter extends PepperImporterImpl implements PepperImporter
{
	public PAULAImporter()
	{
		super();
		
		//setting name of module
		this.name= "PAULAImporter";
		
		//set list of formats supported by this module
		this.addSupportedFormat("paula", "1.0", null);
		
		this.getSDocumentEndings().add(ENDING_LEAF_FOLDER);
		{//just for logging: to say, that the current module has been loaded
			if (this.getLogService()!= null)
				this.getLogService().log(LogService.LOG_DEBUG,this.getName()+" is created...");
		}//just for logging: to say, that the current module has been loaded
	}

//===================================== start: performance variables
	/**
	 * Measured time which is needed to import the corpus structure. 
	 */
	private Long timeImportSCorpusStructure= 0l;
	/**
	 * Measured total time which is needed to import the document corpus structure. 
	 */
	private Long totalTimeImportSDocumentStructure= 0l;
	/**
	 * Measured time which is needed to load all documents into paula model.. 
	 */
	private Long totalTimeToLoadDocument= 0l;
	/**
	 * Measured time which is needed to map all documents to salt. 
	 */
	private Long totalTimeToMapDocument= 0l;
//===================================== end: performance variables
	
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

//// ========================== start: extract corpus-path	
	/**
	 * Stores the endings which are used for paula-files
	 */
	private String[] PAULA_FILE_ENDINGS= {"xml","paula"};
// ========================== end: extract corpus-path
	/**
	 * Extracts properties out of given special parameters.
	 */
	private void exctractProperties()
	{
		if (this.getSpecialParams()!= null)
		{//check if flag for running in parallel is set
			File propFile= new File(this.getSpecialParams().toFileString());
			this.props= new Properties();
			try{
				this.props.load(new FileInputStream(propFile));
			}catch (Exception e)
			{throw new PAULAImporterException("Cannot find input file for properties: "+propFile+"\n nested exception: "+ e.getMessage());}
			if (this.props.containsKey(PROP_RUN_IN_PARALLEL))
			{
				try {
					Boolean val= new Boolean(this.props.getProperty(PROP_RUN_IN_PARALLEL));
					this.setRUN_IN_PARALLEL(val);
				} catch (Exception e) 
				{
					if (this.getLogService()!= null)
						this.getLogService().log(LogService.LOG_WARNING, "Cannot set correct property value of property "+PROP_RUN_IN_PARALLEL+" to "+this.getName()+", because of the value is not castable to Boolean. A correct value can contain 'true' or 'false'.");
				}
			}
			else if (this.props.containsKey(PROP_NUM_OF_PARALLEL_DOCUMENTS))
			{
				try {
					Integer val= new Integer(this.props.getProperty(PROP_NUM_OF_PARALLEL_DOCUMENTS));
					if (val > 0)
						this.setNumOfParallelDocuments(val);
				} catch (Exception e) 
				{
					if (this.getLogService()!= null)
						this.getLogService().log(LogService.LOG_WARNING, "Cannot set correct property value of property "+PROP_NUM_OF_PARALLEL_DOCUMENTS+" to "+this.getName()+", because of the value is not castable to Integer. A correct value must be a positiv, whole number (>0).");
				}
			}
		}//check if flag for running in parallel is set
	}
	
	/**
	 * ThreadPool
	 */
	private ExecutorService executorService= null;
	
	/** Group of all mapper threads of this module **/
	private ThreadGroup threadGroup= null;
	
	// ========================== end: extract corpus-path
	
		@Override
		public void start() throws PepperModuleException
		{
			//creating new thread group for mapper threads
			ThreadGroup activeGroup = new ThreadGroup(Thread.currentThread().getThreadGroup(), this.getName()+"_mapperGroup");
			
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
			
			for (PepperMapperConnector connector: this.getMapperConnectors())
			{
				MAPPING_RESULT result= connector.getMappingResult();
				if (MAPPING_RESULT.DELETED.equals(result))
					this.getPepperModuleController().finish(connector.getSElementId());
				else if (MAPPING_RESULT.FINISHED.equals(result))
					this.getPepperModuleController().put(connector.getSElementId());
				//TODO: set UncoughtExceptionHandler and read it here
			}
			this.end();
		}
		
		/**
		 * A threadsafe list of all {@link PepperMapperConnector} objects which are connected with a started {@link PepperMapper}.
		 */
		private Collection<PepperMapperConnector> mappersConnectors= null;
		
		/**
		 * A lock for method {@link #getMappers()} to create a new mappers list.
		 */
		private Lock getMapperLock= new ReentrantLock(); 
		/**
		 * Returns a threadsafe list of all {@link PepperMapperConnector} objects which are connected with a started {@link PepperMapper}.
		 * @return
		 */
		protected Collection<PepperMapperConnector> getMapperConnectors()
		{
			if (mappersConnectors== null)
			{
				getMapperLock.lock();
				try{
					mappersConnectors= new Vector<PepperMapperConnector>();
				}finally
				{getMapperLock.unlock();}
			}
			return(mappersConnectors);
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
				
				URI resource= getSElementId2ResourceTable().get(sElementId);
				
				PepperMapperConnector connector= new PepperMapperConnectorImpl();
				PepperMapper mapper= new PepperMapperImpl(connector, threadGroup, this.getName()+"_mapper_"+ sElementId);
				mapper.setResourceURI(resource);
				if (sElementId.getSIdentifiableElement() instanceof SDocument)
					mapper.setSDocument((SDocument)sElementId.getSIdentifiableElement());
				else if (sElementId.getSIdentifiableElement() instanceof SCorpus)
					mapper.setSCorpus((SCorpus)sElementId.getSIdentifiableElement());
				mapper.start();
				
				
				
				
//				if (sElementId.getSIdentifiableElement() instanceof SCorpus)
//				{//mapping SCorpus	
//				}//mapping SCorpus
//				if (sElementId.getSIdentifiableElement() instanceof SDocument)
//				{//mapping SDocument
//					SDocument sDocument= (SDocument) sElementId.getSIdentifiableElement();
//					MapperRunner mapperRunner= new MapperRunner();
//					{//configure mapper and mapper runner
//						PAULA2SaltMapper mapper= new PAULA2SaltMapper();
//						mapperRunner.mapper= mapper;
//						mapperRunner.sDocument= sDocument;
//						mapper.setPAULA_FILE_ENDINGS(PAULA_FILE_ENDINGS);
//						mapper.setSDocument(sDocument);
//						mapper.setLogService(this.getLogService());
//					}//configure mapper and mapper runner
//					
//					if (this.getRUN_IN_PARALLEL())
//					{//run import in parallel	
//						this.mapperRunners.add(mapperRunner);
//						this.executorService.execute(mapperRunner);
//					}//run import in parallel
//					else 
//					{//do not run import in parallel
//						mapperRunner.start();
//					}//do not run import in parallel
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
//					URI paulaDoc= sDocumentResourceTable.get(sDocument.getSElementId());
					URI paulaDoc= getSElementId2ResourceTable().get(sDocument.getSElementId());
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
