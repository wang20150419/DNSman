/*
 - Author: otakuchiyan
 - License: GNU GPLv3
 - Description: The impletion and interface class
 */

package io.github.otakuchiyan.dnsman;

import android.content.Context;
import android.content.Intent;
import android.app.IntentService;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.support.v4.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class DNSManager {
    final static String ACTION_SETDNS_DONE = "io.github.otakuchiyan.dnsman.SETDNS_DONE";

    final static String SETPROP_COMMAND_PREFIX = "setprop net.dns";
	final static String GETPROP_COMMAND_PREFIX = "getprop net.dns";
    final static String SETRULE_COMMAND_PREFIX = "iptables -t nat ";
    final static String SETRULE_COMMAND_SUFFIX = " --dport 53 -j DNAT --to-destination ";
    final static String CHECKRULE_COMMAND_PREFIX = "iptables -t nat -L OUTPUT | grep ";

	final static String[] CHECKPROP_COMMANDS = {
        CHECKRULE_COMMAND_PREFIX + "1",
        CHECKRULE_COMMAND_PREFIX + "2"
    };

    private static String hijackedLastDNS = "";
	private static String hijackedLastPort = "";
	private static SharedPreferences sp;
	private static SharedPreferences.Editor sped;
	private static Context context;
	private static List<String> dnsList2set = new ArrayList<String>();
    private List<String> commandsResult = new ArrayList<String>();

	private static boolean checkNetType(NetworkInfo ni){
        if(ni != null && ni.isConnected()){
			return true;
		} else {
			return false;
		}
	}

	public static boolean setDNSByNetType(Context c){
		Log.d("DNSManager", "setDNSByNetType");
		context = c;

        GetNetwork gn = new GetNetwork(c);
        NetworkInfo mobi_res = gn.mobileNetInfo;
        NetworkInfo wifi_res = gn.wifiNetInfo;
        NetworkInfo bt_res = gn.bluetoothNetInfo;
        NetworkInfo eth_res = gn.etherNetInfo;
        NetworkInfo wimax_res = gn.wimaxNetInfo;

		getDNSByPrefix("g");
		if (checkNetType(mobi_res)) {
			getDNSByPrefix("m");
            } else if (checkNetType(wifi_res)) {
                getDNSByPrefix("w");
            } else if (checkNetType(bt_res)) {
                getDNSByPrefix("b");
            } else if (checkNetType(eth_res)) {
                getDNSByPrefix("e");
            } else if (checkNetType(wimax_res)) {
                getDNSByPrefix("wi");
            }


            if (dnsList2set.isEmpty()) {
                return false;
            }
        Log.d("DNSManager[DATA]", "dnsList2set " + dnsList2set.get(0));
        Log.d("DNSManager[DATA]", "dnsList2set " + dnsList2set.get(1));

		DNSManager.setDNS();
		return true;
	}

	private static void setDNS(){
        Bundle dnss_bundle = new Bundle();
		dnss_bundle.putString("dns1", dnsList2set.get(0));
		String dns2suffix = "dns2";
		if (sp.getString("mode", "0").equals("1")) {
			dns2suffix = "port";
		}
		dnss_bundle.putString(dns2suffix, dnsList2set.get(1));
		//DNSBackgroundIntentService.performAction(context, dnss_bundle);
	}

	private static void getDNSByPrefix(final String net_prefix){
		sp = PreferenceManager.getDefaultSharedPreferences(
				context.getApplicationContext());
		List<String> l = new ArrayList<String>();
		String dns2suffix = "dns2";
		String dns1 = sp.getString(net_prefix + "dns1", "");

		if(sp.getString("mode", "1").equals("1")) {
			dns2suffix = "port";
		}
		String dns2 = sp.getString(net_prefix + dns2suffix, "");
		if(!dns1.equals("")|| !dns2.equals("")){
			l.clear();
			l.add(dns1);
			l.add(dns2);
		}

		if(!l.isEmpty()){
			dnsList2set = l;
		}

	}

	public static boolean setDNSViaSetprop(String dns1, String dns2) {
		String[] setCommands = {
            SETPROP_COMMAND_PREFIX + "1 \"" + dns1 + "\"",
            SETPROP_COMMAND_PREFIX + "2 \"" + dns2 + "\""
        };

        Log.d("DNSManager[CMD]", setCommands[0]);
        Log.d("DNSManager[CMD]", setCommands[1]);

        if(Shell.SU.available()){
            Shell.SU.run(setCommands);
        }else{
            Shell.SH.run(setCommands);
        }

        List<String> result = Shell.SH.run(CHECKPROP_COMMANDS);

		//Check effect
		if(!result.get(0).equals(dns1) ||
			!result.get(1).equals(dns2)){
            return false;
        }
		
        return true;
    }

	public static boolean setDNSViaIPtables(String dns, String port){
		sp = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
		sped = sp.edit();

        if(isRulesAlivable(context)){
            if(hijackedLastDNS.equals(dns)){
                return true;
            }
        }

        String usedPort = !port.equals("") ? port : "53";

        if(!hijackedLastDNS.equals(dns)) {
            deleteRules();
			hijackedLastDNS = dns;
			hijackedLastPort = usedPort;
			sped.putString("hijackedLastDNS", dns);
            sped.putString("hijackedLastPort", port);
            sped.apply();
        }


        List<String> cmds = new ArrayList<String>();
        List<String> result;
        cmds.add(SETRULE_COMMAND_PREFIX + "-A OUTPUT -p udp" + SETRULE_COMMAND_SUFFIX + dns + ":" + usedPort);
        cmds.add(SETRULE_COMMAND_PREFIX + "-A OUTPUT -p tcp" + SETRULE_COMMAND_SUFFIX + dns + ":" + usedPort);
        Log.d("DNSManager[CMD]", cmds.get(0));
        Log.d("DNSManager[CMD]", cmds.get(1));

        result = Shell.SU.run(cmds);
        return result.isEmpty();
    }

    private static List<String> deleteRules(){
        List<String> cmds = new ArrayList<String>();
		if (hijackedLastDNS.equals("") && hijackedLastPort.equals("")) {
			sp = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
			hijackedLastDNS = sp.getString("hijackedLastDNS", "");
			hijackedLastPort = sp.getString("hijackedLastPort", "");
		}
		if (hijackedLastPort.equals("")) {
			hijackedLastPort = "53";
		}


		if(!hijackedLastDNS.equals("")) {
			cmds.add(SETRULE_COMMAND_PREFIX + "-D OUTPUT -p udp" + SETRULE_COMMAND_SUFFIX + hijackedLastDNS + ":" + hijackedLastPort);
			cmds.add(SETRULE_COMMAND_PREFIX + "-D OUTPUT -p tcp" + SETRULE_COMMAND_SUFFIX + hijackedLastDNS + ":" + hijackedLastPort);
			Log.d("DNSManager[CMD]", cmds.get(0));
			Log.d("DNSManager[CMD]", cmds.get(1));
		}
        return Shell.SU.run(cmds);
    }

    private static boolean isRulesAlivable(Context context){
		sp = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        List<String> cmds = new ArrayList<String>();
		if(hijackedLastDNS.equals("") && hijackedLastPort.equals("")){
			hijackedLastDNS = sp.getString("hijackedLastDNS", "");
			hijackedLastPort = sp.getString("hijackedLastPort", "");
		}

        if(hijackedLastPort.equals("")){
            hijackedLastPort = "53";
        }

		if(!hijackedLastDNS.equals("")) {
			cmds.add(CHECKRULE_COMMAND_PREFIX + hijackedLastDNS + ":" + hijackedLastPort);
            Log.d("DNSManager[CMD]", cmds.get(0));
		}
        return !Shell.SU.run(cmds).isEmpty();
    }
	
	public static List<String> writeResolvConf(String dns1, String dns2, String path){
        List<String> cmds = new ArrayList<String>();
		List<String> result;
		
		cmds.add("mount -o remount,rw /system");
		if(!dns1.equals("")){
		    cmds.add("echo nameserver " + dns1 + " > " + path);
		}
		if(!dns2.equals("")){
			cmds.add("echo nameserver " + dns2 + " >> " + path);
		}
        cmds.add("chmod 644 " + path);
        cmds.add("mount -o remount,ro /system");
		return Shell.SU.run(cmds);
	}
	
	public static List<String> removeResolvConf(String path){
		String[] cmds = {
			"mount -o remount,rw /system",
			"rm " + path,
			"mount -o remount,ro /system"
		};
		return Shell.SU.run(cmds);
	}
	
	public static List<String> getCurrentDNS(){
		return Shell.SH.run(CHECKPROP_COMMANDS);
	}

    public static String getResolvConf(String path){
        StringBuilder sb = new StringBuilder();
        for(String s : Shell.SH.run("cat " + path + " | grep 'nameserver'")){
            sb.append(s.replace("nameserver", ""));
            sb.append("\n");
        }
        return sb.toString();
    }

    private class RunCommandService extends IntentService{
        public RunCommandService(){
            super("RunCommandService");
        }

        public void performAction(Context c, Bundle dnss){
            if(c == null){
                return;
            }
/*
            Intent i = new Intent(c, DNSBackgroundIntentService.class);
            i.putExtras(dnss);
            c.startService(i);
            */
        }


        @Override
        protected void onHandleIntent(Intent i){
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            Bundle dnss = i.getExtras();
            boolean result;

            if (sp.getString("mode", "0").equals("1")) {
                result = DNSManager.setDNSViaIPtables(dnss.getString("dns1"), dnss.getString("port"));
            } else {
                result = DNSManager.setDNSViaSetprop(dnss.getString("dns1"), dnss.getString("dns2"));
            }

            Intent result_intent = new Intent(ACTION_SETDNS_DONE);
            result_intent.putExtra("result", result);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(result_intent);
        }
    }
    
}
