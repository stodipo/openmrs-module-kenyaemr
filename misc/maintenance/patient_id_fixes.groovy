/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

/**
 * Checks all patients for duplicate or missing patient identifiers
 */

import org.openmrs.PatientIdentifier
import org.openmrs.api.context.Context

def openmrsIdType = Context.patientService.getPatientIdentifierTypeByUuid("dfacd928-0370-4315-99d7-6ec1c9f7ae76")

def idgenSvc = Context.getService(Context.loadClass("org.openmrs.module.idgen.service.IdentifierSourceService"))

def defaultLocationGP = Context.administrationService.getGlobalProperty("kenyaemr.defaultLocation")
def defaultLocation = defaultLocationGP ? Context.locationService.getLocation(defaultLocationGP.toInteger()) : null

// Check script can be run on this installation
if (!openmrsIdType) {
	return "Can't find the OpenMRS ID identifier type"
}
if (!defaultLocation) {
	return "Default location must be set"
}
if (!idgenSvc) {
	return "Can't access IDGen service"
}

def duplicates = 0, fixed_duplicates = 0, missing = 0, fixed_missing = 0;

// Check for duplicate and missing identifier values
for (def patient : Context.patientService.allPatients) {
	def idsByType = [:]

	patient.activeIdentifiers.each { id ->
		if (idsByType.containsKey(id.identifierType)) {
			def existing = idsByType[id.identifierType]
			duplicates++

			if (existing.identifier == id.identifier) {
				println "Patient #" + patient.id + " has duplicate identifiers of type " + id.identifierType + " so voiding this one"
				Context.patientService.voidPatientIdentifier(id, "Duplicate found by script")
				fixed_duplicates++
			}
			else {
				println "Patient #" + patient.id + " has multiple identifiers of type " + id.identifierType + " (value1=" + id.identifier + ", value2=" + existing.identifier + ")"
			}
		} else {
			idsByType.put(id.identifierType, id)
		}
	}

	if (!idsByType.containsKey(openmrsIdType)) {
		println "Patient #" + patient.id + " is missing the required OpenMRS ID"
		missing++

		def generated = idgenSvc.generateIdentifier(openmrsIdType, "ID fix script");
		def generatedOpenmrsId = new PatientIdentifier(generated, openmrsIdType, defaultLocation);
		patient.addIdentifier(generatedOpenmrsId);
		Context.patientService.savePatient(patient)
		fixed_missing++
	}
}

println "=================== Summary ======================"
println "Duplicate ID problems: " + duplicates + " (" + fixed_duplicates + " fixed)"
println "Missing ID problems: " + missing + " (" + fixed_missing + " fixed)"