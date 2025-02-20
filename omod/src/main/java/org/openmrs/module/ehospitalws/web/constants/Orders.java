package org.openmrs.module.ehospitalws.web.constants;

import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class Orders {
	
	public static List<Order> getPatientTestOrders(String patientUuid) {
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		if (patient == null) {
			return Collections.emptyList();
		}
		
		Visit latestVisit = getLatestVisit(patient);
		if (latestVisit == null) {
			return Collections.emptyList();
		}
		
		OrderType testOrderType = Context.getOrderService().getOrderTypeByUuid("52a447d3-a64a-11e3-9aeb-50e549534c5e");
		
		if (testOrderType == null) {
			return Collections.emptyList();
		}
		
		// Fetch all orders for the patient and filter based on latest visit
		return Context.getOrderService().getAllOrdersByPatient(patient).stream()
		        .filter(order -> testOrderType.equals(order.getOrderType()))
		        .filter(order -> latestVisit.getEncounters().contains(order.getEncounter())).collect(Collectors.toList());
	}
	
	public static List<DrugOrder> getPatientMedications(String patientUuid) {
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		if (patient == null) {
			return Collections.emptyList();
		}
		
		Visit latestVisit = getLatestVisit(patient);
		if (latestVisit == null) {
			return Collections.emptyList();
		}
		
		OrderType drugOrderType = Context.getOrderService().getOrderTypeByUuid("131168f4-15f5-102d-96e4-000c29c2a5d7");
		
		if (drugOrderType == null) {
			return Collections.emptyList();
		}
		
		// Fetch all orders and filter only DrugOrders for the latest visit
		return Context.getOrderService().getAllOrdersByPatient(patient).stream().filter(order -> order instanceof DrugOrder)
		        .map(order -> (DrugOrder) order).filter(drugOrder -> drugOrder.getOrderType().equals(drugOrderType))
		        .filter(drugOrder -> latestVisit.getEncounters().contains(drugOrder.getEncounter()))
		        .collect(Collectors.toList());
	}
	
	public static List<Condition> getPatientConditions(String patientUuid) {
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		if (patient == null) {
			return Collections.emptyList();
		}
		
		Visit latestVisit = getLatestVisit(patient);
		if (latestVisit == null) {
			return Collections.emptyList();
		}
		
		// Fetch conditions only for the latest visit
		return Context.getConditionService().getActiveConditions(patient).stream()
		        .filter(condition -> condition.getEncounter() != null)
		        .filter(condition -> latestVisit.getEncounters().contains(condition.getEncounter()))
		        .collect(Collectors.toList());
	}
	
	private static Visit getLatestVisit(Patient patient) {
		List<Visit> visits = Context.getVisitService().getVisitsByPatient(patient);
		return visits.stream().max((v1, v2) -> v1.getStartDatetime().compareTo(v2.getStartDatetime())).orElse(null);
	}
}
