package com.plugin.core;

import android.app.Application;
import android.content.pm.ActivityInfo;

import com.plugin.content.PluginActivityInfo;
import com.plugin.content.PluginDescriptor;
import com.plugin.content.PluginIntentFilter;
import com.plugin.content.PluginProviderInfo;
import com.plugin.util.LogUtil;
import com.plugin.util.ManifestReader;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PluginManifestParser {

	public static PluginDescriptor parseManifest(String pluginPath) {
    	
        try {
        	ZipFile zipFile = new ZipFile(new File(pluginPath), ZipFile.OPEN_READ);
            ZipEntry manifestXmlEntry = zipFile.getEntry(ManifestReader.DEFAULT_XML);
        	String manifestXml = ManifestReader.getManifestXMLFromAPK(zipFile, manifestXmlEntry);
        	
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(manifestXml));
            int eventType = parser.getEventType();
            String namespaceAndroid = null;
            String packageName = null;

            ArrayList<String> dependencies = null;

            PluginDescriptor desciptor = new PluginDescriptor();
            do {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT: {
                        break;
                    }
                    case XmlPullParser.START_TAG: {
                        String tag = parser.getName();
                        if ("manifest".equals(tag)) {
                        	
                            namespaceAndroid = parser.getNamespace("android");
                            
                            packageName = parser.getAttributeValue(null, "package");
                            String versionCode = parser.getAttributeValue(namespaceAndroid, "versionCode");
                            String versionName = parser.getAttributeValue(namespaceAndroid, "versionName");
                           
                            //用这个字段来标记apk是独立apk，还是需要依赖主程序的class和resource
                            //当这个值等于宿主程序packageName时，则认为这个插件是需要依赖宿主的class和resource的
                            String sharedUserId = parser.getAttributeValue(namespaceAndroid, "sharedUserId");
                           
                            desciptor.setPackageName(packageName);
                            desciptor.setVersion(versionName + "_" + versionCode);
                            
                            desciptor.setStandalone(sharedUserId == null || !PluginLoader.getApplication().getPackageName().equals(sharedUserId));

                            LogUtil.d(packageName, versionCode, versionName, sharedUserId);
                        } else if ("meta-data".equals(tag)) {

                        	String name = parser.getAttributeValue(namespaceAndroid, "name");
                        	String value = parser.getAttributeValue(namespaceAndroid, "value");

                            if (name != null) {

//                                HashMap<String, String> metaData = desciptor.getMetaData();
//                                if (metaData == null) {
//                                    metaData = new HashMap<String, String>();
//                                    desciptor.setMetaData(metaData);
//                                }
//                                if (value != null && value.startsWith("@") && value.length() == 9) {
//                                    String idHex = value.replace("@", "");
//                                    try {
//                                        int id = Integer.parseInt(idHex, 16);
//                                        value = Integer.toString(id);
//                                    } catch (Exception e) {
//                                        e.printStackTrace();
//                                    }
//                                }
//                                metaData.put(name, value);

                                LogUtil.d("meta-data", name, value);

                            }

                        } else if ("exported-fragment".equals(tag)) {

                            String name = parser.getAttributeValue(namespaceAndroid, "name");
                            String value = parser.getAttributeValue(namespaceAndroid, "value");
                            value = getName(value, packageName);
                            if (name != null) {

                                HashMap<String, String> fragments = desciptor.getFragments();
                                if (fragments == null) {
                                    fragments = new HashMap<String, String>();
                                    desciptor.setfragments(fragments);
                                }
                                fragments.put(name, value);
                                LogUtil.d(name, value);

                            }

                        } else if ("exported-service".equals(tag)) {

                            String name = parser.getAttributeValue(namespaceAndroid, "name");
                            String value = parser.getAttributeValue(namespaceAndroid, "value");
                            value = getName(value, packageName);
                            if (name != null) {

                                HashMap<String, String> functions = desciptor.getFunctions();
                                if (functions == null) {
                                    functions = new HashMap<String, String>();
                                    desciptor.setFunctions(functions);
                                }
                                functions.put(name, value);
                                LogUtil.d(name, value);

                            }

                        } else if ("uses-library".equals(tag)) {

                            String name = parser.getAttributeValue(namespaceAndroid, "name");

                            if (dependencies == null) {
                                dependencies = new ArrayList<String>();
                            }
                            dependencies.add(name);

                        } else if ("application".equals(tag)) {
                        	
                        	String applicationName = parser.getAttributeValue(namespaceAndroid, "name");
                            if (applicationName == null) {
                                applicationName = Application.class.getName();
                            }
                            applicationName = getName(applicationName, packageName);
                            desciptor.setApplicationName(applicationName);

                    		desciptor.setDescription(parser.getAttributeValue(namespaceAndroid, "label"));

                            //这里不解析主题，后面会通过packageManager查询

                            LogUtil.d("applicationName", applicationName, " Description ", desciptor.getDescription());

                        } else if ("activity".equals(tag)) {

                            String windowSoftInputMode = parser.getAttributeValue(namespaceAndroid, "windowSoftInputMode");//strin
                            String hardwareAccelerated = parser.getAttributeValue(namespaceAndroid, "hardwareAccelerated");//int string
                            String launchMode = parser.getAttributeValue(namespaceAndroid, "launchMode");//string
                            String screenOrientation = parser.getAttributeValue(namespaceAndroid, "screenOrientation");//string
                            String theme = parser.getAttributeValue(namespaceAndroid, "theme");//int
                            String immersive = parser.getAttributeValue(namespaceAndroid, "immersive");//int string
                            String uiOptions = parser.getAttributeValue(namespaceAndroid, "uiOptions");//int string

                            HashMap<String, ArrayList<PluginIntentFilter>> map = desciptor.getActivitys();
                            if (map == null) {
                                map = new HashMap<String, ArrayList<PluginIntentFilter>>();
                                desciptor.setActivitys(map);
                            }
                            String name = addIntentFilter(map, packageName, namespaceAndroid, parser, "activity");

                            HashMap<String, PluginActivityInfo> infos = desciptor.getActivityInfos();
                            if (infos == null) {
                                infos = new HashMap<String, PluginActivityInfo>();
                                desciptor.setActivityInfos(infos);
                            }

                            PluginActivityInfo pluginActivityInfo = infos.get(name);
                            if (pluginActivityInfo == null) {
                                pluginActivityInfo = new PluginActivityInfo();
                                infos.put(name, pluginActivityInfo);
                            }
                            pluginActivityInfo.setHardwareAccelerated(hardwareAccelerated);
                            pluginActivityInfo.setImmersive(immersive);
                            if (launchMode == null) {
                                launchMode = String.valueOf(ActivityInfo.LAUNCH_MULTIPLE);
                            }
                            pluginActivityInfo.setLaunchMode(launchMode);
                            pluginActivityInfo.setName(name);
                            pluginActivityInfo.setScreenOrientation(screenOrientation);
                            pluginActivityInfo.setTheme(theme);
                            pluginActivityInfo.setWindowSoftInputMode(windowSoftInputMode);
                            pluginActivityInfo.setUiOptions(uiOptions);

                        } else if ("receiver".equals(tag)) {

                            HashMap<String, ArrayList<PluginIntentFilter>> map = desciptor.getReceivers();
                            if (map == null) {
                                map = new HashMap<String, ArrayList<PluginIntentFilter>>();
                                desciptor.setReceivers(map);
                            }
                            addIntentFilter(map, packageName, namespaceAndroid, parser, "receiver");

                        } else if ("service".equals(tag)) {

                            String process = parser.getAttributeValue(namespaceAndroid, "process");

                            HashMap<String, ArrayList<PluginIntentFilter>> map = desciptor.getServices();
                            if (map == null) {
                                map = new HashMap<String, ArrayList<PluginIntentFilter>>();
                                desciptor.setServices(map);
                            }
                        	String name = addIntentFilter(map, packageName, namespaceAndroid, parser, "service");

                            if (process != null) {
                                HashMap<String, String> infos = desciptor.getServiceInfos();
                                if (infos == null) {
                                    infos = new HashMap<String, String>();
                                    desciptor.setServiceInfos(infos);
                                }
                                infos.put(name, process);
                            }

                        } else if ("provider".equals(tag)) {

                            String name = parser.getAttributeValue(namespaceAndroid, "name");
                            name = getName(name, packageName);
                            String author = parser.getAttributeValue(namespaceAndroid, "authorities");
                            String exported = parser.getAttributeValue(namespaceAndroid, "exported");
                            HashMap<String, PluginProviderInfo> providers = desciptor.getProviderInfos();
                            if (providers == null) {
                                providers = new HashMap<String, PluginProviderInfo>();
                                desciptor.setProviderInfos(providers);
                            }

                            PluginProviderInfo info = new PluginProviderInfo();
                            info.setName(name);
                            info.setExported(Boolean.getBoolean(exported));
                            info.setAuthority(author);

                            providers.put(name, info);
                        }
                        break;
                    }
                    case XmlPullParser.END_TAG: {
                        break;
                    }
                }
                eventType = parser.next();
            } while (eventType != XmlPullParser.END_DOCUMENT);
            
            desciptor.setEnabled(true);

            //有可能没有配置application节点，这里需要检查一下application
            if (desciptor.getApplicationName() == null) {
                desciptor.setApplicationName(Application.class.getName());
            }

            if (dependencies != null) {
                desciptor.setDependencies((String[])dependencies.toArray(new String[0]));
            }

            return desciptor;
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
	private static String addIntentFilter(HashMap<String, ArrayList<PluginIntentFilter>> map, String packageName, String namespace,
			XmlPullParser parser, String endTagName) throws XmlPullParserException, IOException {
		int eventType = parser.getEventType();
		String activityName = parser.getAttributeValue(namespace, "name");
		activityName = getName(activityName, packageName);

		ArrayList<PluginIntentFilter> filters = map.get(activityName);
		if (filters == null) {
			filters = new ArrayList<PluginIntentFilter>();
            map.put(activityName, filters);
		}
		
		PluginIntentFilter intentFilter = new PluginIntentFilter();
		do {
			switch (eventType) {
				case XmlPullParser.START_TAG: {
					String tag = parser.getName();
					if ("intent-filter".equals(tag)) {
						intentFilter = new PluginIntentFilter();
						filters.add(intentFilter);
					} else {
						intentFilter.readFromXml(tag, parser);
					}
				}
			}
			eventType = parser.next();
		} while (!endTagName.equals(parser.getName()));//再次到达，表示一个标签结束了

        return activityName;
	}
	
	private static String getName(String nameOrig, String pkgName) {
        if (nameOrig == null) {
            return null;
        }
        StringBuilder sb = null;
        if (nameOrig.startsWith(".")) {
            sb = new StringBuilder();
            sb.append(pkgName);
            sb.append(nameOrig);
        } else if (!nameOrig.contains(".")) {
            sb = new StringBuilder();
            sb.append(pkgName);
            sb.append('.');
            sb.append(nameOrig);
        } else {
            return nameOrig;
        }
        return sb.toString();
    }

}
