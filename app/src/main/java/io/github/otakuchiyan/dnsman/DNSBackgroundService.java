package io.github.otakuchiyan.dnsman;

import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;
import android.app.IntentService;
import android.net.NetworkInfo;

import java.util.List;
import java.util.ArrayList;

import io.github.otakuchiyan.dnsman.DNSManager;

public class DNSBackgroundService extends IntentService{
    final static String ACTION_SETDNS_DONE = "io.github.otakuchiyan.dnsman.SETDNS_DONE";

    private static Context context;
    private static SharedPreferences sp;
    private static SharedPreferences.Editor sped;
    private static List<String> dnsList = new ArrayList<String>();
    private static boolean checkProp = true;
    private static String mode;

    public DNSBackgroundService(){
        super("DNSBackgroundService");
    }

    public static boolean start(Context c, NetworkInfo info){
	    sp = PreferenceManager.getDefaultSharedPreferences(
			    context.getApplicationContext());
        context = c;
	getDNSByNetType(info);
	if(dnsList.isEmpty()){
		return false;
	}
	checkProp = sp.getBoolean("checkprop", true);
	mode = sp.getString("mode", "0");
	Intent i = new Intent(c, DNSBackgroundService.class);
	c.startService(i);
	return true;
    }

    private static void getDNSByNetType(NetworkInfo info){
	    sp = PreferenceManager.getDefaultSharedPreferences(
			    context.getApplicationContext());
	    String dns1 = sp.getString(info.getTypeName() + "dns1", "");
	    String dns2suffix = "dns2";
	    //dns2 was used for port when mode is 1
	    if(mode.equals("1")){
		    dns2suffix = "port";
	    }
	    String dns2 = sp.getString(info.getTypeName() + dns2suffix, "");
	    
	    if(!dns1.equals("") || !dns2.equals("")){
		    dnsList.clear();
		    dnsList.add(dns1);
		    dnsList.add(dns2);
	    }
    }

    @Override
    protected void onHandleIntent(Intent i){
        boolean result = false;
        switch(mode){
            case "0":
                result = DNSManager.setDNSViaSetprop(dnsList.get(0), dnsList.get(1), checkProp);
                break;
            case "1":
                result = DNSManager.setDNSViaIPtables(dnsList.get(0), dnsList.get(1));
                break;
        }
        Intent result_intent = new Intent(ACTION_SETDNS_DONE);
        i.putExtra("result", result);
        LocalBroadcastManager.getInstance(context).sendBroadcast(result_intent);
    }

}
