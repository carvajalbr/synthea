package org.mitre.synthea.export;

import com.google.common.collect.Table;

import ca.uhn.fhir.parser.IParser;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Bundle.BundleType;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.IntegerType;
import org.hl7.fhir.dstu3.model.Organization;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Provider;

public abstract class HospitalExporterStu3 {

  private static final String SYNTHEA_URI = "http://synthetichealth.github.io/synthea/";

  /**
   * Export the hospital in FHIR STU3 format.
   */
  public static void export(long stop) {
    if (Config.getAsBoolean("exporter.hospital.fhir_stu3.export")) {

      Bundle bundle = new Bundle();
      if (Config.getAsBoolean("exporter.fhir.transaction_bundle")) {
        bundle.setType(BundleType.BATCH);
      } else {
        bundle.setType(BundleType.COLLECTION);
      }
      for (Provider h : Provider.getProviderList()) {
        // filter - exports only those hospitals in use
        Table<Integer, String, AtomicInteger> utilization = h.getUtilization();
        int totalEncounters = utilization.column(Provider.ENCOUNTERS).values().stream()
            .mapToInt(ai -> ai.get()).sum();
        if (totalEncounters > 0) {
          BundleEntryComponent entry = FhirStu3.provider(bundle, h);
          addHospitalExtensions(h, (Organization) entry.getResource());
        }
      }

      boolean ndjson = Config.getAsBoolean("exporter.fhir.bulk_data", false);
      File outputFolder = Exporter.getOutputFolder("fhir_stu3", null);
      IParser parser = FhirStu3.getContext().newJsonParser();

      if (ndjson) {
        Path outFilePath = outputFolder.toPath().resolve("Organization." + stop + ".ndjson");
        for (BundleEntryComponent entry : bundle.getEntry()) {
          String entryJson = parser.encodeResourceToString(entry.getResource());
          Exporter.appendToFile(outFilePath, entryJson);
        }
      } else {
        parser = parser.setPrettyPrint(true);
        Path outFilePath = outputFolder.toPath().resolve("hospitalInformation" + stop + ".json");
        String bundleJson = parser.encodeResourceToString(bundle);
        Exporter.overwriteFile(outFilePath, bundleJson);
      }
    }
  }

  /**
   * Add FHIR extensions to capture additional information.
   */
  public static void addHospitalExtensions(Provider h, Organization organizationResource) {
    Table<Integer, String, AtomicInteger> utilization = h.getUtilization();
    // calculate totals for utilization
    int totalEncounters = utilization.column(Provider.ENCOUNTERS).values().stream()
        .mapToInt(ai -> ai.get()).sum();
    Extension encountersExtension = new Extension(SYNTHEA_URI + "utilization-encounters-extension");
    IntegerType encountersValue = new IntegerType(totalEncounters);
    encountersExtension.setValue(encountersValue);
    organizationResource.addExtension(encountersExtension);

    int totalProcedures = utilization.column(Provider.PROCEDURES).values().stream()
        .mapToInt(ai -> ai.get()).sum();
    Extension proceduresExtension = new Extension(SYNTHEA_URI + "utilization-procedures-extension");
    IntegerType proceduresValue = new IntegerType(totalProcedures);
    proceduresExtension.setValue(proceduresValue);
    organizationResource.addExtension(proceduresExtension);

    int totalLabs = utilization.column(Provider.LABS).values().stream().mapToInt(ai -> ai.get())
        .sum();
    Extension labsExtension = new Extension(SYNTHEA_URI + "utilization-labs-extension");
    IntegerType labsValue = new IntegerType(totalLabs);
    labsExtension.setValue(labsValue);
    organizationResource.addExtension(labsExtension);

    int totalPrescriptions = utilization.column(Provider.PRESCRIPTIONS).values().stream()
        .mapToInt(ai -> ai.get()).sum();
    Extension prescriptionsExtension = new Extension(
        SYNTHEA_URI + "utilization-prescriptions-extension");
    IntegerType prescriptionsValue = new IntegerType(totalPrescriptions);
    prescriptionsExtension.setValue(prescriptionsValue);
    organizationResource.addExtension(prescriptionsExtension);

    Integer bedCount = h.getBedCount();
    if (bedCount != null) {
      Extension bedCountExtension = new Extension(SYNTHEA_URI + "bed-count-extension");
      IntegerType bedCountValue = new IntegerType(bedCount);
      bedCountExtension.setValue(bedCountValue);
      organizationResource.addExtension(bedCountExtension);
    }
  }
}