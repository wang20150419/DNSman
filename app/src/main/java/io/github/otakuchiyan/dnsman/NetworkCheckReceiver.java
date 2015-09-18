package io.github.otakuchiyan.dnsman;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.text.TextUtils;
import android.widget.Toast;
import android.util.Log;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.app.NotificationCompat;
import android.content.IntentFilter;

import eu.chainfire.libsuperuser.Shell;

import io.github.otakuchiyan.dnsman.DNSManager;
import io.github.otakuchiyan.dnsman.MainActivity;
import io.github.otakuchiyan.dnsman.DNSBackgroundIntentService;
import io.github.otakuchiyan.dnsman.GetNetwork;

import android.app.*;
import java.util.List;
import java.util.ArrayList;

public class NetworkCheckReceiver extends BroadcastReceiver {
	private NotificationManager nm;
    private ArrayList<String> dnsList2set = new ArrayList<String>();
    private SharedPreferences sp;

    private BroadcastReceiver dnsSetted = new BroadcastReceiver(){
	    @Override
	    public void onReceive(Context c, Intent i){
		if(i.getAction().equals(DNSBackgroundIntentService.ACTION_SETDNS_DONE)){
		    if(i.getBooleanExtra("result", false)){
			Toast.makeText(c, R.string.set_succeed, Toast.LENGTH_SHORT).show();
		}else{
			Toast.makeText(c, R.string.set_failed, Toast.LENGTH_SHORT).show();
		}
		}
	    }
	};

    private void setDNS(Context c, final int net_type) {
        sp = PreferenceManager.getDefaultSharedPreferences(
                c.getApplicationContext());

		Bundle dnss_bundle = new Bundle();
		
		IntentFilter iFilter = new IntentFilter();
		iFilter.addAction(DNSBackgroundIntentService.ACTION_SETDNS_DONE);
		LocalBroadcastManager.getInstance(c).registerReceiver(dnsSetted, iFilter);

        setDNSArrayByPrefix("g");
        switch(net_type){
            case ConnectivityManager.TYPE_WIFI:
                setDNSArrayByPrefix("w");
                break;
            case ConnectivityManager.TYPE_MOBILE:
                setDNSArrayByPrefix("m");
                break;
            case ConnectivityManager.TYPE_BLUETOOTH:
                setDNSArrayByPrefix("b");
                break;
            case ConnectivityManager.TYPE_ETHERNET:
                setDNSArrayByPrefix("e");
                break;
            case ConnectivityManager.TYPE_WIMAX:
                setDNSArrayByPrefix("wi");
                break;
        }

        Log.d("DNSman", "dns1 = " + dnsList2set.get(0));
        Log.d("DNSman", "dns2 = " + dnsList2set.get(1));
		
		if(dnsList2set.get(0).equals("") && dnsList2set.get(1).equals("")){
		    NotificationCompat.Builder ncb =
			new NotificationCompat.Builder(c)
			.setSmallIcon(android.R.drawable.stat_notify_error)
			.setContentTitle(c.getText(R.string.nodns_noti))
			.setContentText(c.getText(R.string.nodns_noti_text));
		    Intent mainIntent = new Intent(c, MainActivity.class);

		    TaskStackBuilder tsb = TaskStackBuilder.create(c);
		    tsb.addParentStack(MainActivity.class);
		    tsb.addNextIntent(mainIntent);
		    PendingIntent mainPendingIntent =
			tsb.getPendingIntent(0,
					     PendingIntent.FLAG_UPDATE_CURRENT);
		    ncb.setContentIntent(mainPendingIntent);
		    nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
		    nm.notify(0, ncb.build());
			return;
		}
		dnss_bundle.putString("dns1", dnsList2set.get(0));
		dnss_bundle.putString("dns2", dnsList2set.get(1));
		
		DNSBackgroundIntentService.performAction(c, dnss_bundle);
		
    }

    public void getDNSByPrefix(final String net_prefix){
        dnsList2set.add(sp.getString(net_prefix + "dns1", ""));
        dnsList2set.add(sp.getString(net_prefix + "dns2", ""));
        
    }

    public void setDNSArrayByPrefix(final String prefix){
        getDNSByPrefix(prefix);
    }


    public void onReceive(Context context, Intent intent) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(
			context.getApplicationContext());
		
		if(sp.getBoolean("firstbooted", false)){
			GetNetwork.init(context);
			NetworkInfo mobi_res  = GetNetwork.getMobileNetInfo();
			NetworkInfo wifi_res  = GetNetwork.getWiFiNetInfo();
            NetworkInfo bt_res    = GetNetwork.getBluetoothNetInfo();
            NetworkInfo eth_res   = GetNetwork.getEthernetNetInfo();
            NetworkInfo wimax_res = GetNetwork.getWiMaxNetInfo();
            if(checkNetType(mobi_res)){
                setDNS(context, ConnectivityManager.TYPE_MOBILE);
            } else if (checkNetType(wifi_res)){
                setDNS(context, ConnectivityManager.TYPE_WIFI);
            } else if (checkNetType(bt_res)){
                setDNS(context, ConnectivityManager.TYPE_BLUETOOTH);
            } else if (checkNetType(eth_res)){
                setDNS(context, ConnectivityManager.TYPE_ETHERNET);
            } else if (checkNetType(wimax_res)){
                setDNS(context, ConnectivityManager.TYPE_WIMAX);
            }
        }
    }

    private static boolean checkNetType(NetworkInfo ni){
        if(ni != null && ni.isConnected()){
            return true;
        } else {
            return false;
        }
    }
}

