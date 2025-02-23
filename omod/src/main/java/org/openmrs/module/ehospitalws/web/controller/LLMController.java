package org.openmrs.module.ehospitalws.web.controller;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

import static org.openmrs.module.ehospitalws.web.constants.Constants.*;
import static org.openmrs.module.ehospitalws.web.constants.Orders.*;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ehospital")
public class LLMController {
	
	@RequestMapping(method = RequestMethod.GET, value = "/patient/encounter")
	@ResponseBody
	public Object getAllPatients(HttpServletRequest request, @RequestParam("patientUuid") String patientUuid)
	        throws ParseException, IOException {
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		if (patient == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"error\": \"Patient not found\"}");
		}
		
		ObjectNode patientData = generatePatientObject(null, null, null, patient);
		
		ObjectMapper mapper = new ObjectMapper();
		String jsonString = mapper.writeValueAsString(patientData);
		
		return ResponseEntity.ok(jsonString);
	}
	
	private static ObjectNode generatePatientObject(Date startDate, Date endDate, filterCategory filterCategory,
	        Patient patient) {
		ObjectNode patientObj = JsonNodeFactory.instance.objectNode();
		Date birthdate = patient.getBirthdate();
		Date currentDate = new Date();
		long age = (currentDate.getTime() - birthdate.getTime()) / (1000L * 60 * 60 * 24 * 365);

		String diagnosis = getPatientDiagnosis(patient);
		if (diagnosis == null || diagnosis.trim().isEmpty()) {
			diagnosis = "N/A";
		}
		
		Double weight = getPatientWeight(patient);
		Double height = getPatientHeight(patient);
		Double bmi = getPatientBMI(patient);
		Integer systolic_blood_pressure = getPatientSystolicPressure(patient);
		Integer diastolic_blood_pressure = getPatientDiastolicPressure(patient);
		String blood_pressure = (systolic_blood_pressure != null && diastolic_blood_pressure != null)
		        ? systolic_blood_pressure + "/" + diastolic_blood_pressure
		        : "N/A";
		Integer heart_rate = getPatientHeartRate(patient);
		Double temperature = getPatientTemperature(patient);
		
		patientObj.put("sex", patient.getGender());
		patientObj.put("age", age);
		patientObj.put("weight", weight != null ? weight : 0.0);
		patientObj.put("height", height != null ? height : 0.0);
		patientObj.put("bmi", bmi != null ? bmi : 0.0);
		patientObj.put("blood_pressure", blood_pressure);
		patientObj.put("heart_rate", heart_rate != null ? heart_rate : 0);
		patientObj.put("temperature", temperature != null ? temperature : 0.0);
		patientObj.put("diagnosis", diagnosis);

		List<Order> testOrders = getPatientTestOrders(patient.getUuid());
		List<DrugOrder> medications = getPatientMedications(patient.getUuid());
		List<Condition> conditions = getPatientConditions(patient.getUuid());
		
		ArrayNode testsArray = patientObj.putArray("tests");
		
		for (Order testOrder : testOrders) {
			if (testOrder.getConcept() != null && testOrder.getConcept().getDisplayString() != null) {
				ObjectNode testObj = JsonNodeFactory.instance.objectNode();
				testObj.put("test_name", testOrder.getConcept().getDisplayString());

				ArrayNode testResultsArray = testObj.putArray("test_results");
				
				List<Obs> testObservations = getTestObservations(patient.getUuid(), testOrder.getConcept().getUuid());
				
				for (Obs obs : testObservations) {
					ObjectNode resultObj = JsonNodeFactory.instance.objectNode();
					resultObj.put("parameter", obs.getConcept().getName().getName());
					resultObj.put("value", obs.getValueAsString(Context.getLocale()));
					
					testResultsArray.add(resultObj);
				}
				
				testsArray.add(testObj);
			}
		}
		
		ArrayNode medicationsArray = patientObj.putArray("medication");
		for (DrugOrder medOrder : medications) {
			if (medOrder.getDrug() != null) {
				medicationsArray.add(medOrder.getDrug().getName());
			} else if (medOrder.getConcept() != null && medOrder.getConcept().getName() != null) {
				medicationsArray.add(medOrder.getConcept().getName().getName());
			}
		}
		
		ArrayNode conditionsArray = patientObj.putArray("condition");
		for (Condition condition : conditions) {
			if (condition.getCondition() != null) {
				if (condition.getCondition().getCoded() != null) {
					conditionsArray.add(condition.getCondition().getCoded().getName().getName());
				} else if (condition.getCondition().getNonCoded() != null) {
					conditionsArray.add(condition.getCondition().getNonCoded());
				}
			}
		}
		
		System.out.println("Generated JSON: " + patientObj.toString());
		
		return patientObj;
	}
	
	/**
	 * Generates a summary of patient data within a specified date range, grouped by year, month, and
	 * week.
	 * 
	 * @param allPatients A set of all patients to be considered for the summary.
	 * @param startDate The start date of the range for which to generate the summary.
	 * @param endDate The end date of the range for which to generate the summary.
	 * @param filterCategory The category to filter patients.
	 * @return A JSON string representing the summary of patient data.
	 */
	public Object generatePatientListObj(HashSet<Patient> allPatients, Date startDate, Date endDate,
	        filterCategory filterCategory, ObjectNode allPatientsObj) {
		
		ArrayNode patientList = JsonNodeFactory.instance.arrayNode();
		
		List<Date> patientDates = new ArrayList<>();
		Calendar startCal = Calendar.getInstance();
		startCal.setTime(startDate);
		Calendar endCal = Calendar.getInstance();
		endCal.setTime(endDate);
		
		for (Patient patient : allPatients) {
			ObjectNode patientObj = generatePatientObject(startDate, endDate, filterCategory, patient);
			if (patientObj != null) {
				patientList.add(patientObj);
				
				Calendar patientCal = Calendar.getInstance();
				patientCal.setTime(patient.getDateCreated());
				
				if (!patientCal.before(startCal) && !patientCal.after(endCal)) {
					patientDates.add(patient.getDateCreated());
				}
			}
		}
		
		ObjectNode groupingObj = JsonNodeFactory.instance.objectNode();
		allPatientsObj.put("summary", groupingObj);
		
		return allPatientsObj.toString();
	}
}
