package com.plugin.core;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;

import com.plugin.content.PluginActivityInfo;
import com.plugin.content.PluginDescriptor;
import com.plugin.content.PluginReceiverIntent;
import com.plugin.core.manager.PluginManagerHelper;
import com.plugin.util.LogUtil;
import com.plugin.util.RefInvoker;

import java.util.ArrayList;

public class PluginIntentResolver {

	public static final String CLASS_SEPARATOR = "_RECEIVER_AND_ACTIVITY_";
	public static final String CLASS_PREFIX_RECEIVER = "_RECEIVER_";
	public static final String CLASS_PREFIX_SERVICE = "_SERVICE_";

	public static void resolveService(Intent service) {
		ArrayList<String> classNameList = PluginLoader.matchPlugin(service, PluginDescriptor.SERVICE);
		if (classNameList != null && classNameList.size() > 0) {
			String stubServiceName = PluginManagerHelper.bindStubService(classNameList.get(0));
			if (stubServiceName != null) {
				service.setComponent(new ComponentName(PluginLoader.getApplication().getPackageName(), stubServiceName));
			}
		}
	}

	public static ArrayList<Intent> resolveReceiver(final Intent intent) {
		// 如果在插件中发现了匹配intent的receiver项目，替换掉ClassLoader
		// 不需要在这里记录目标className，className将在Intent中传递
		ArrayList<Intent> result = new ArrayList<Intent>();
		ArrayList<String> classNameList = PluginLoader.matchPlugin(intent, PluginDescriptor.BROADCAST);
		if (classNameList != null && classNameList.size() > 0) {
			for(String className: classNameList) {
				Intent newIntent = new Intent(intent);
				newIntent.setComponent(new ComponentName(PluginLoader.getApplication().getPackageName(),
						PluginManagerHelper.bindStubReceiver()));
				//hackReceiverForClassLoader检测到这个标记后会进行替换
				newIntent.setAction(className + CLASS_SEPARATOR + (intent.getAction() == null ? "" : intent.getAction()));
				result.add(newIntent);
			}
		} else {
			result.add(intent);
		}
		return result;
	}

	/* package */static Class resolveReceiverForClassLoader(Object msgObj) {
		Intent intent = (Intent) RefInvoker.getFieldObject(msgObj, "android.app.ActivityThread$ReceiverData", "intent");
		if (intent.getComponent().getClassName().equals(PluginManagerHelper.bindStubReceiver())) {
			String action = intent.getAction();
			LogUtil.d("action", action);
			if (action != null) {
				String[] targetClassName = action.split(CLASS_SEPARATOR);
				@SuppressWarnings("rawtypes")
				Class clazz = PluginLoader.loadPluginClassByName(targetClassName[0]);
				if (clazz != null) {
					intent.setExtrasClassLoader(clazz.getClassLoader());
					//由于之前intent被修改过 这里再吧Intent还原到原始的intent
					if (targetClassName.length > 1) {
						intent.setAction(targetClassName[1]);
					} else {
						intent.setAction(null);
					}
				}
				// PluginClassLoader检测到这个特殊标记后会进行替换
				intent.setComponent(new ComponentName(intent.getComponent().getPackageName(),
						CLASS_PREFIX_RECEIVER + targetClassName[0]));

				if (Build.VERSION.SDK_INT >= 21) {
					if (intent.getExtras() != null) {
						PluginReceiverIntent newIntent = new PluginReceiverIntent(intent);
						RefInvoker.setFieldObject(msgObj, "android.app.ActivityThread$ReceiverData", "intent", newIntent);
					}
				}

				return clazz;
			}
		}
		return null;
	}

	/* package */static String resolveServiceForClassLoader(Object msgObj) {
		ServiceInfo info = (ServiceInfo) RefInvoker.getFieldObject(msgObj, "android.app.ActivityThread$CreateServiceData", "info");
		//通过映射查找
		String targetClassName = PluginManagerHelper.getBindedPluginServiceName(info.name);
		//TODO 或许可以通过这个方式来处理service
		//info.applicationInfo = XXX

		LogUtil.d("hackServiceName", info.name, info.packageName, info.processName, "targetClassName", targetClassName, info.applicationInfo.packageName);

		if (targetClassName != null) {
			info.name =  CLASS_PREFIX_SERVICE + targetClassName;
		} else {
			LogUtil.e("hackServiceName 没有找到映射关系, 有2个可能：1、确实是宿主service；2、映射表出了异常。如果是映射表出了异常会导致classNotFound", info.name);
			PluginManagerHelper.dumpServiceInfo();
		}
		return info.name;
	}

	public static void resolveActivity(Intent intent) {
		// 如果在插件中发现Intent的匹配项，记下匹配的插件Activity的ClassName
		ArrayList<String> classNameList = PluginLoader.matchPlugin(intent, PluginDescriptor.ACTIVITY);
		if (classNameList != null && classNameList.size() > 0) {

			String className = classNameList.get(0);
			PluginDescriptor pd = PluginManagerHelper.getPluginDescriptorByClassName(className);

			PluginActivityInfo pluginActivityInfo = pd.getActivityInfos().get(className);

			String stubActivityName = PluginManagerHelper.bindStubActivity(className, Integer.parseInt(pluginActivityInfo.getLaunchMode()));

			intent.setComponent(
					new ComponentName(PluginLoader.getApplication().getPackageName(), stubActivityName));
			//PluginInstrumentationWrapper检测到这个标记后会进行替换
			intent.setAction(className + CLASS_SEPARATOR + (intent.getAction()==null?"":intent.getAction()));
		}
	}

	/* package */static void resolveActivity(Intent[] intent) {
		// 不常用。需要时再实现此方法，
	}

}
