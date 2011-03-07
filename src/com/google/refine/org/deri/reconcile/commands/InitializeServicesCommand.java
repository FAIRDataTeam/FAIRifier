package com.google.refine.org.deri.reconcile.commands;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import com.google.refine.org.deri.reconcile.model.ReconciliationService;
import com.google.refine.org.deri.reconcile.sindice.SindiceBroker;
import com.google.refine.org.deri.reconcile.sindice.SindiceService;
import com.google.refine.org.deri.reconcile.util.GRefineJsonUtilitiesImpl;
import com.google.refine.org.deri.reconcile.util.RdfUtilitiesImpl;
import org.json.JSONArray;
import org.json.JSONException;

import com.google.refine.org.deri.reconcile.GRefineServiceManager;
import com.google.refine.util.ParsingUtilities;


public class InitializeServicesCommand extends AbstractAddServiceCommand{

	@Override
	protected ReconciliationService getReconciliationService(HttpServletRequest request) throws JSONException, IOException {
		ReconciliationService service = new SindiceService("sindice", "Sindice", null, 
				new GRefineJsonUtilitiesImpl(), new RdfUtilitiesImpl(), new SindiceBroker());
		try {
			JSONArray arr = ParsingUtilities.evaluateJsonStringToArray(request.getParameter("services"));
			Set<String> urls = new HashSet<String>();
			for(int i=0;i<arr.length();i++){
				urls.add(arr.getString(i));
			}
			GRefineServiceManager.singleton.synchronizeServices(urls);
			GRefineServiceManager.singleton.addService(service);
			return service;
		} catch (JSONException e) {
			throw new RuntimeException("Failed to initialize Sindice service", e);
		}
	}
}
