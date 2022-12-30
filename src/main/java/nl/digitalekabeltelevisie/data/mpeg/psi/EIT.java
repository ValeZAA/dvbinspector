package nl.digitalekabeltelevisie.data.mpeg.psi;
/**
 *
 *  http://www.digitalekabeltelevisie.nl/dvb_inspector
 *
 *  This code is Copyright 2009-2022 by Eric Berendsen (e_berendsen@digitalekabeltelevisie.nl)
 *
 *  This file is part of DVB Inspector.
 *
 *  DVB Inspector is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  DVB Inspector is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with DVB Inspector.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  The author requests that he be notified of any application, applet, or
 *  other binary that makes use of this code, but that's more out of curiosity
 *  than anything and is not required.
 *
 */

import static nl.digitalekabeltelevisie.data.mpeg.descriptors.Descriptor.findDescriptorApplyListFunc;
import static nl.digitalekabeltelevisie.util.Utils.addListJTree;
import static nl.digitalekabeltelevisie.util.Utils.getDurationMillis;
import static nl.digitalekabeltelevisie.util.Utils.getUTCDate;
import static nl.digitalekabeltelevisie.util.Utils.isUndefined;
import static nl.digitalekabeltelevisie.util.Utils.simpleModus;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.swing.table.TableModel;
import javax.swing.tree.DefaultMutableTreeNode;

import org.apache.commons.lang3.ArrayUtils;

import nl.digitalekabeltelevisie.controller.KVP;
import nl.digitalekabeltelevisie.data.mpeg.PSI;
import nl.digitalekabeltelevisie.data.mpeg.descriptors.ParentalRatingDescriptor;
import nl.digitalekabeltelevisie.data.mpeg.psi.EITsection.Event;
import nl.digitalekabeltelevisie.gui.EITableImage;
import nl.digitalekabeltelevisie.util.Interval;
import nl.digitalekabeltelevisie.util.ServiceIdentification;
import nl.digitalekabeltelevisie.util.Utils;
import nl.digitalekabeltelevisie.util.tablemodel.FlexTableModel;
import nl.digitalekabeltelevisie.util.tablemodel.TableHeader;
import nl.digitalekabeltelevisie.util.tablemodel.TableHeaderBuilder;


/**
 *
 * Based on 5.2.4 Event Information Table (EIT) of ETSI EN 300 468 V1.11.1 (2010-04)
 *
 * @author Eric
 *
 */
public class EIT extends AbstractPSITabel{

	private static final String EVENT_GRID_TITLE = "Grid";
	private static final String EVENTS_SCHEDULE_TITLE = "Events Schedule";

	//     original_network_id,transport_stream_id,  serviceId, table_id 
	private final Map<Integer, TreeMap<Integer, TreeMap<Integer, TreeMap<Integer,EITsection []>>>> newEit = 
			new TreeMap<>();
	
	private static final Logger	logger	= Logger.getLogger(EIT.class.getName());


	public EIT(final PSI parent){
		super(parent);
	}

	public void update(final EITsection section){

		final int original_network_id = section.getOriginalNetworkID();
		final int streamId = section.getTransportStreamID();
		final int serviceId = section.getServiceID();
		final int tableId = section.getTableId();

		TreeMap<Integer, TreeMap<Integer, TreeMap<Integer, EITsection[]>>> networkSections = newEit.computeIfAbsent(original_network_id,k -> new TreeMap<>());
		TreeMap<Integer, TreeMap<Integer, EITsection[]>> programStreamSections = networkSections.computeIfAbsent(streamId,k -> new TreeMap<>());
		TreeMap<Integer, EITsection[]> serviceSections = programStreamSections.computeIfAbsent(serviceId,k -> new TreeMap<>());
		EITsection[] tableSectionArray = serviceSections.computeIfAbsent(tableId,k -> new EITsection[section.getSectionLastNumber()+1]);
		
		if(tableSectionArray.length<=section.getSectionNumber()){ //resize if needed
			tableSectionArray = Arrays.copyOf(tableSectionArray, section.getSectionNumber()+1);
			serviceSections.put(tableId,tableSectionArray);
		}
		
		if(tableSectionArray[section.getSectionNumber()]==null){
			tableSectionArray[section.getSectionNumber()] = section;
		}else{
			final TableSection last = tableSectionArray[section.getSectionNumber()];
			updateSectionVersion(section, last);
		}
	}

	@Override
	public DefaultMutableTreeNode getJTreeNode(final int modus) {

		// need this KVP at end of loop to set ImageSource
		final KVP eitKVP = new KVP("EIT");
		final DefaultMutableTreeNode t = new DefaultMutableTreeNode(eitKVP);
		
		Map<ServiceIdentification, EITsection[]> allEitImageMap = new TreeMap<>();
		
		for(Entry<Integer, TreeMap<Integer, TreeMap<Integer, TreeMap<Integer, EITsection[]>>>> network:newEit.entrySet()) {
			final Integer orgNetworkId= network.getKey();
			TreeMap<Integer, TreeMap<Integer, TreeMap<Integer, EITsection[]>>> networkSections = network.getValue();
			
			// need this KVP at end of loop to set ImageSource
			final KVP networkNodeKVP = new KVP("original_network_id", orgNetworkId,Utils.getOriginalNetworkIDString(orgNetworkId));
			final DefaultMutableTreeNode networkNode = new DefaultMutableTreeNode(networkNodeKVP);
			t.add(networkNode);
			
			Map<ServiceIdentification, EITsection[]> networkImageMap = new TreeMap<>();

			for(Entry<Integer, TreeMap<Integer, TreeMap<Integer, EITsection[]>>> netWorkSection:networkSections.entrySet()) {
				final Integer transport_stream_id = netWorkSection.getKey();
				TreeMap<Integer, TreeMap<Integer, EITsection[]>> streams = netWorkSection.getValue();

				// need this KVP at end of loop to set ImageSource
				final KVP streamNodeKVP = new KVP("transport_stream_id", transport_stream_id,null);
				final DefaultMutableTreeNode streamNode = new DefaultMutableTreeNode(streamNodeKVP);
				networkNode.add(streamNode);

				TreeMap<ServiceIdentification, EITsection[]> streamImageMap = new TreeMap<>();

				for(Entry<Integer, TreeMap<Integer, EITsection[]>> streamEntry: streams.entrySet()) {
					final Integer serviceId = streamEntry.getKey();
					TreeMap<Integer, EITsection[]> service = streamEntry.getValue();
					
					// for EITImage, sections of this service with tableID >80
					EITsection[] serviceSections = new EITsection[0];
				
					final KVP serviceNodeKVP = new KVP("service_id", serviceId,getParentPSI().getSdt().getServiceName(orgNetworkId, transport_stream_id, serviceId));
					final DefaultMutableTreeNode serviceNode = new DefaultMutableTreeNode(serviceNodeKVP);
										
					serviceNodeKVP.addHTMLSource(() -> service.entrySet().
							stream().
							filter(s -> s.getKey()>=80).
							map(Entry::getValue).
							flatMap(Arrays::stream).
							filter(Objects::nonNull).
							map(e -> e.getHtmlForEit(modus)).
							collect(Collectors.joining("","<b>Schedule</b><br><br>","")),
							"List");
					
					serviceNodeKVP.addTableSource(() -> getTableModelForService(service.entrySet()), EVENTS_SCHEDULE_TITLE);
					
					streamNode.add(serviceNode);
				
					for(Entry<Integer, EITsection[]> serviceEntry : service.entrySet()) {
						Integer tableId = serviceEntry.getKey();
						EITsection[] sections = serviceEntry.getValue();

						final KVP tableNodeKVP = new KVP("tableid", tableId,TableSection.getTableType(tableId));
						tableNodeKVP.addHTMLSource(() -> Arrays.stream(sections).
								filter(Objects::nonNull).
								map(e -> e.getHtmlForEit(modus)).
								collect(Collectors.joining()),
								"List"
								);

						tableNodeKVP.addTableSource(()->getTableModel(sections), "Events");
						final DefaultMutableTreeNode tableNode = new DefaultMutableTreeNode(tableNodeKVP);
						serviceNode.add(tableNode);

						if(tableId>=80) {
							serviceSections = appendSections(serviceSections, sections);
						}
						

						for (EITsection section : sections) {
							if(section!= null){
								if(!simpleModus(modus)){
									addSectionVersionsToJTree(tableNode, section, modus);
								}else{
									addListJTree(tableNode,section.getEventList(),modus,"events");
								}
							}
						}
					}
					// now all sections for service are in serviceSections
					streamImageMap.put(new ServiceIdentification(orgNetworkId, transport_stream_id, serviceId), serviceSections);
				}
				streamNodeKVP.addImageSource(new EITableImage(this, streamImageMap),EVENT_GRID_TITLE);
				streamNodeKVP.addTableSource(() ->getTableModelForStream(streamImageMap.values()),EVENTS_SCHEDULE_TITLE);
				
				networkImageMap.putAll(streamImageMap);
			}
			networkNodeKVP.addImageSource(new EITableImage(this, networkImageMap),EVENT_GRID_TITLE);
			networkNodeKVP.addTableSource(() ->getTableModelForStream(networkImageMap.values()),EVENTS_SCHEDULE_TITLE);
			allEitImageMap.putAll(networkImageMap);
		}
		eitKVP.addImageSource(new EITableImage(this, allEitImageMap),EVENT_GRID_TITLE);
		eitKVP.addTableSource(() ->getTableModelForStream(allEitImageMap.values()),EVENTS_SCHEDULE_TITLE);
		return t;

	}

	/**
	 * @param collection
	 * @return
	 */
	private static TableModel getTableModelForStream(Collection<EITsection[]> collection) {
		FlexTableModel<EITsection,Event> tableModel =  new FlexTableModel<>(buildEitTableHeader());
		
		for( EITsection[] sections:collection) {
			for(EITsection section:sections) {
				if(section != null) {
					tableModel.addData(section, section.getEventList());
				}
			}
		}

		tableModel.process();
		return tableModel;
	}


	/**
	 * @param entrySet
	 * @return
	 */
	private static TableModel getTableModelForService(Set<Entry<Integer, EITsection[]>> entrySet) {
		FlexTableModel<EITsection,Event> tableModel =  new FlexTableModel<>(buildEitTableHeader());

		for(Entry<Integer, EITsection[]> entry:entrySet) {
			if(entry.getKey() >= 80) {
				EITsection[] sections = entry.getValue();
				for(EITsection section:sections) {
					if(section != null) {
						tableModel.addData(section, section.getEventList());
					}
				}
			}
		}

		tableModel.process();
		return tableModel;
	}

	/**
	 * @param sections
	 * @return
	 */
	private static TableModel getTableModel(EITsection[] sections) {
		FlexTableModel<EITsection,Event> tableModel =  new FlexTableModel<>(buildEitTableHeader());

		for(EITsection section:sections) {
			if(section != null) {
				tableModel.addData(section, section.getEventList());
			}
			
		}
		

		tableModel.process();
		return tableModel;
	}

	/**
	 * @param serviceSections
	 * @param sections
	 * @return
	 */
	private static EITsection[] appendSections(EITsection[] serviceSections, EITsection[] sections) {
		EITsection[] tmpArray = new EITsection[serviceSections.length + sections.length];
		System.arraycopy(serviceSections, 0, tmpArray, 0, serviceSections.length);
		System.arraycopy(sections, 0, tmpArray, serviceSections.length, sections.length);
		return tmpArray;
	}
	

	/**
	 * Returns the start time of the first, and the end time of the last event of the services in this EIT table.
	 * Only the services contained in serviceSet are used in the calculation
	 *
	 * @param serviceSet service ID of services to be included in calculation
	 * @param eitTable map of service IDs to EITSection[] Can contain sections from different Table IDs, like 0x50 and 0x51, etc... (for very long EPGs)
	 * @return Interval that covers all events in eitTable
	 */
	public static Interval getSpanningInterval(final Set<ServiceIdentification> serviceSet, Map<ServiceIdentification, EITsection[]> eitTable) {
		Date startDate = null;
		Date endDate = null;
		// services to be displayed

		for(final ServiceIdentification serviceNo : serviceSet){
			for(final EITsection section :eitTable.get(serviceNo)){
				if(section!= null){
					List<Event> eventList = section.getEventList();
					for(Event event:eventList){
						final byte[] startTime = event.getStartTime();
						if(isUndefined(startTime)){ // undefined start time
							continue;
						}
						Date eventStart = getUTCDate( startTime);
						if((startDate==null)||(startDate.after(eventStart))){
							startDate = eventStart;
						}
						if(eventStart!=null){
							try{
								Date eventEnd = new Date(eventStart.getTime()+ getDurationMillis(event.getDuration()));
								if((endDate==null)||(endDate.before(eventEnd))){
									endDate = eventEnd;
								}
							}catch(NumberFormatException nfe){
								logger.log(Level.WARNING, "getSpanningInterval: Event.duration is not a valid BCD number;", nfe);
							}
						}
					}
				}
			}
		}
		if((startDate!=null)&&(endDate!=null)){
			return new Interval(startDate,endDate);
		}
		return null;
	}

	public Map<ServiceIdentification, EITsection[]> getCombinedSchedule() {
		return getFlatEit(tableId -> tableId >= 80);
	}

	public Map<ServiceIdentification, EITsection[]> getCombinedPresentFollowing() {
		return getFlatEit(tableId -> tableId < 80);
	}

	public Map<ServiceIdentification, EITsection[]> getFlatEit(IntPredicate scheduleOrPF) {
		Map<ServiceIdentification, EITsection[]> result = new TreeMap<>();

		for (Entry<Integer, TreeMap<Integer, TreeMap<Integer, TreeMap<Integer, EITsection[]>>>> networkEntry : newEit.entrySet()) {
			int orgNetworkId = networkEntry.getKey();
			TreeMap<Integer, TreeMap<Integer, TreeMap<Integer, EITsection[]>>> network = networkEntry.getValue();

			for (Entry<Integer, TreeMap<Integer, TreeMap<Integer, EITsection[]>>> streamEntry : network.entrySet()) {
				int streamId = streamEntry.getKey();
				TreeMap<Integer, TreeMap<Integer, EITsection[]>> stream = streamEntry.getValue();

				for (Entry<Integer, TreeMap<Integer, EITsection[]>> serviceEntry : stream.entrySet()) {
					int serviceId = serviceEntry.getKey();
					TreeMap<Integer, EITsection[]> service = serviceEntry.getValue();

					for (Entry<Integer, EITsection[]> tableEntry : service.entrySet()) {
						int tableId = tableEntry.getKey();
						if (scheduleOrPF.test(tableId)) {
							result.merge(new ServiceIdentification(orgNetworkId, streamId, serviceId),
									tableEntry.getValue(),
									ArrayUtils::addAll);
						}
					}
				}
			}
		}
		return result;
	}

	
	static TableHeader<EITsection, Event> buildEitTableHeader() {

		return new TableHeaderBuilder<EITsection, Event>()
				.addRequiredBaseColumn("onid", EITsection::getOriginalNetworkID, Integer.class)
				.addRequiredBaseColumn("tsid", EITsection::getTransportStreamID, Integer.class)
				.addRequiredBaseColumn("sid", EITsection::getServiceID, Integer.class)
				.addRequiredBaseColumn("service", EITsection::findServiceName, String.class)

				.addRequiredRowColumn("Start time", e -> Utils.getEITStartTimeAsString(e.getStartTime()), String.class)
				.addRequiredRowColumn("duration", e -> Utils.formatDuration(e.getDuration()), String.class)
				.addRequiredRowColumn("Event ID", Event::getEventID, Integer.class)

				.addRequiredRowColumn("Event Name", Event::getEventName, String.class)

				.addOptionalRepeatingRowColumn("rating ", component -> findDescriptorApplyListFunc(
						component.getDescriptorList(), ParentalRatingDescriptor.class,
						ratingDescriptor -> ratingDescriptor.getRatingList().stream().collect(Collectors.toList())),
						ParentalRatingDescriptor.Rating.class)
				.build();
	}

}
