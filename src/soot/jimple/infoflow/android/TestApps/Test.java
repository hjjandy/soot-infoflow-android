/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow.android.TestApps;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.AbstractInfoflowProblem.PathTrackingMethod;
import soot.jimple.infoflow.InfoflowResults;
import soot.jimple.infoflow.InfoflowResults.SinkInfo;
import soot.jimple.infoflow.InfoflowResults.SourceInfo;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;

public class Test {
	
	private static final class MyResultsAvailableHandler implements
			ResultsAvailableHandler {
		private final BufferedWriter wr;

		private MyResultsAvailableHandler() {
			this.wr = null;
		}

		private MyResultsAvailableHandler(BufferedWriter wr) {
			this.wr = wr;
		}

		@Override
		public void onResultsAvailable(
				BiDiInterproceduralCFG<Unit, SootMethod> cfg,
				InfoflowResults results) {
			// Dump the results
			if (results == null) {
				print("No results found.");
			}
			else {
				for (SinkInfo sink : results.getResults().keySet()) {
					print("Found a flow to sink " + sink + ", from the following sources:");
					for (SourceInfo source : results.getResults().get(sink)) {
						print("\t- " + source.getSource() + " (in "
								+ cfg.getMethodOf(source.getContext()).getSignature()  + ")");
						if (source.getPath() != null && !source.getPath().isEmpty())
							print("\t\ton Path " + source.getPath());
					}
				}
			}
		}

		private void print(String string) {
			try {
				System.out.println(string);
				if (wr != null)
					wr.write(string + "\n");
			}
			catch (IOException ex) {
				// ignore
			}
		}
	}
	
	static String command;
	static boolean generate = false;
	
	private static int timeout = -1;
	private static int sysTimeout = -1;
	
	private static boolean DEBUG = false;

	/**
	 * @param args[0] = path to apk-file
	 * @param args[1] = path to android-dir (path/android-platforms/)
	 */
	public static void main(final String[] args) throws IOException, InterruptedException {
		if (args.length < 2) {
			printUsage();	
			return;
		}
		
		//start with cleanup:
		File outputDir = new File("JimpleOutput");
		if (outputDir.isDirectory()){
			boolean success = true;
			for(File f : outputDir.listFiles()){
				success = success && f.delete();
			}
			if(!success){
				System.err.println("Cleanup of output directory "+ outputDir + " failed!");
			}
			outputDir.delete();
		}
		
		// Parse additional command-line arguments
		parseAdditionalOptions(args);
		if (!validateAdditionalOptions())
			return;
		
		List<String> apkFiles = new ArrayList<String>();
		File apkFile = new File(args[0]);
		if (apkFile.isDirectory()) {
			String[] dirFiles = apkFile.list(new FilenameFilter() {
					
				@Override
				public boolean accept(File dir, String name) {
					if (name.equals("v2_com.fsck.k9_1_16024_K-9 Mail.apk")
							|| name.equals("v2_com.byril.battleship_1_8_Schiffeversenken.apk")
							|| name.equals("v2_mobi.mgeek.TunnyBrowser_1_203_Dolphin Browser.apk") // broken APK
							|| name.equals("v2_com.bigduckgames.flow_1_20_Flow Free.apk")
							|| name.equals("v2_com.starfinanz.smob.android.sfinanzstatus_1_20727_Sparkasse.apk") // type mask not found for type nb
							|| name.equals("v2_com.zoosk.zoosk_1_85_Zoosk.apk") // apk looks broken, references non-existing field in system class
							|| name.equals("v2_com.zoosk.zoosk_1_85_Zoosk - Online-Dating.apk") // apk looks broken, references non-existing field in system class
//							|| name.equals("v2_com.ebay.mobile_1_30_Offizielle eBay-App.apk") // dexpler fails
							|| name.equals("v2_com.streetspotr.streetspotr_1_30_Streetspotr.apk") // type mask not found for type com.streetspotr.streetspotr.map.ViewMapHomelocation
							|| name.equals("v2_com.game.BMX_Boy_1_8_BMX Boy.apk") // out of memory
							|| name.equals("v2_com.hm_1_143_H&M.apk")		// Debug?
							|| name.equals("v2_com.trustgo.mobile.security_1_34_Antivirus & Mobile Security.apk") // out of memory
							|| name.equals("v2_com.bfs.papertoss_1_7005_Paper Toss.apk")	// runs forever
							|| name.equals("v2_com.magmamobile.game.Smash_1_3_Smash.apk")	// runs forever
							|| name.equals("v2_com.autoscout24_1_95_AutoScout24 - mobile Autosuche.apk") // type mask not found
							|| name.equals("v2_com.gameloft.android.ANMP.GloftMTHM_1_1090_World at Arms.apk")	// runs forever
							|| name.equals("v2_com.opera.browser_1_1301080958_Opera Mobile web browser.apk")	// exception in SPARK, missing API class
							|| name.equals("v2_com.google.android.music_1_914_Google Play Music.apk")	// exception in SPARK, missing API class
							|| name.equals("v2_com.disney.WMWLite_1_15_Where's My Water_ Free.apk")	// runs forever
//							|| name.equals("v2_com.aldiko.android_1_200196_Aldiko Book Reader.apk") // dexpler fails
							|| name.equals("v2_com.andromo.dev10265.app194711_1_12_Berlin Tag & Nacht - Quiz.apk") // runs forever
							|| name.equals("v2_com.navigon.navigator_select_1_10804801_NAVIGON select.apk")	// exception in SPARK, missing API class
							|| name.equals("v2_com.kvadgroup.photostudio_1_20_Photo Studio.apk")	// broken APK (zip error)
							|| name.equals("v2_com.vectorunit.yellow_1_9_Beach Buggy Blitz.apk")	// runs forever
							|| name.equals("v2_com.iudesk.android.photo.editor_1_2013032310_Photo Editor.apk") // out of memory
							|| name.equals("v2_com.evernote_1_15020_Evernote.apk")	// broken APK, class missing
							|| name.equals("v2_appinventor.ai_progetto2003.SCAN_1_9_QR BARCODE SCANNER.apk") // main method blowup
							|| name.equals("v2_com.game.SkaterBoy_1_8_Skater Boy.apk")	// runs forever
							|| name.equals("v2_com.teamlava.fashionstory21_1_2_Fashion Story_ Earth Day.apk") // missing class
//							|| name.equals("v2_com.bestcoolfungames.antsmasher_1_80_Ameisen-Quetscher Kostenlos.apk")	// dexpler fails
//							|| name.equals("v2_com.spotify.mobile.android.ui_1_51200052_Spotify.apk")	// dexpler fails
							|| name.equals("v2_com.adobe.air_1_3600609_Adobe AIR.apk")	// Dexpler missing superclass?
							|| name.equals("v2_com.gamehivecorp.kicktheboss2_1_15_Kick the Boss 2.apk")	// runs forever
							|| name.equals("v2_com.nqmobile.antivirus20_1_214_NQ Mobile Security& Antivirus.apk")	// Dexpler missing superclass?
//							|| name.equals("v2_tunein.player_1_47_TuneIn Radio.apk") // dexpler fails
							|| name.equals("v2_com.lmaosoft.hangmanDE_1_24_Hangman - Deutsch-Spiel.apk")	// runs forever
							|| name.equals("v2_tv.dailyme.android_1_105_dailyme TV, Serien & Fernsehen.apk")	// broken apk? field ref
//							|| name.equals("v2_com.twitter.android_1_400_Twitter.apk")	// dexpler fails
							|| name.equals("v2_de.radio.android_1_39_radio.de.apk")	// runs forever
							|| name.equals("v2_com.magmamobile.game.Burger_1_8_Burger.apk")	// broken apk? field ref
//							|| name.equals("v2_com.rovio.angrybirdsspace.ads_1_1510_Angry Birds Space.apk")	// dexpler fails
							|| name.equals("v2_de.barcoo.android_1_50_Barcode Scanner barcoo.apk")	// runs forever
							|| name.equals("v2_www.agathasmaze.com.slenderman_1_26_SlenderMan.apk") // out of memory
							|| name.equals("v2_com.netbiscuits.kicker_1_11_kicker online.apk")	// field missing
							|| name.equals("v2_com.appturbo.appturboDE_1_2_App des Tages - 100% Gratis.apk")	// runs forever
							|| name.equals("v2_de.msg_1_37_mehr-tanken.apk")	// runs forever
							|| name.equals("v2_com.wetter.androidclient_1_26_wetter.com.apk")	// StackOverflowException in dexpler
							|| name.equals("v2_mobi.infolife.taskmanager_1_84_Advanced Task Manager Deutsch.apk")	// runs forever
//							|| name.equals("v2_com.feelingtouch.strikeforce2_1_7_SWAT_End War.apk")		// dexpler fails
							|| name.equals("v2_com.ebay.kleinanzeigen_1_294_eBay Kleinanzeigen.apk")	// field missing
							|| name.equals("v2_com.gravitylabs.photobooth_1_5_Photo Effects Pro.apk")	// out of memory
							|| name.equals("v2_com.rcd.radio90elf_1_488_90elf Fussball Bundesliga Live.apk")	// runs forever
							|| name.equals("v2_logo.quiz.game.category_1_20_Ultimate Logo Quiz.apk")	// missing field
							|| name.equals("v2_de.avm.android.fritzapp_1_1538_FRITZ!App Fon.apk")	// out of memory
							|| name.equals("v2_com.droidhen.game.poker_1_35_DH Texas Poker.apk")	// broken apk? field ref
//							|| name.equals("v2_com.avast.android.mobilesecurity_1_4304_avast! Mobile Security.apk")	// dexpler bug
							|| name.equals("v2_de.rtl.video_1_5_RTL INSIDE.apk")		// broken apk? field ref
							|| name.equals("v2_jp.naver.line.android_1_107_LINE_ Gratis-Anrufe.apk")	// broken apk? field ref
//							|| name.equals("v2_com.zeptolab.ctr.ads_1_20_Cut the Rope FULL FREE.apk")	// Dexpler issue
//							|| name.equals("v2_com.advancedprocessmanager_1_59_Android Assistant(18 features).apk")		// Dexpler issue
							|| name.equals("v2_com.rcflightsim.cvplane2_1_42_Absolute RC Plane Sim.apk")	// out of memory
//							|| name.equals("v2_com.zynga.livepoker_1_77_Zynga Poker.apk")	// dexpler fails
							|| name.equals("v2_com.baudeineapp.mcdcoupon_1_4_McDonalds Gutscheine App.apk")		// broken apk? field ref
//							|| name.equals("v2_de.sellfisch.android.wwr_1_56_Wer Wird Reich (Quiz).apk")		// dexpler fails
							|| name.equals("v2_com.aviary.android.feather_1_82_Photo Editor von Aviary.apk")		// runs forever
							
							|| name.equals("v2_com.maxmpz.audioplayer_1_525_Poweramp.apk")	// runs forever
							|| name.equals("v2_com.ebay.mobile_1_30_Offizielle eBay-App.apk")	// runs forever
							|| name.equals("v2_com.aldiko.android_1_200196_Aldiko Book Reader.apk")	// runs forever
							|| name.equals("v2_com.bestcoolfungames.antsmasher_1_80_Ameisen-Quetscher Kostenlos.apk")	// runs forever
							|| name.equals("v2_tunein.player_1_47_TuneIn Radio.apk")	// out of memory
							|| name.equals("v2_com.twitter.android_1_400_Twitter.apk")	// runs forever
							|| name.equals("v2_com.feelingtouch.strikeforce2_1_7_SWAT_End War.apk")	// out of memory
							|| name.equals("v2_ch.smalltech.battery.free_1_126_Akku & Batterie HD.apk")	// runs forever
							|| name.equals("v2_com.androidgonzalez2013.buggie_1_5_Hugo Runner.apk")		// runs forever
							|| name.equals("v2_com.avast.android.mobilesecurity_1_4304_avast! Mobile Security.apk")		// runs forever
							|| name.equals("v2_com.zynga.livepoker_1_77_Zynga Poker.apk")		// runs forever
							|| name.equals("v2_de.sellfisch.android.wwr_1_56_Wer Wird Reich (Quiz).apk")		// runs forever
							|| name.equals("v2_com.picsart.studio_1_60_PicsArt - Photo Studio.apk")			// runs forever
							|| name.equals("v2_com.androidity.vecchi_1_5_Old Face - vor sterben.apk")		// runs forever
							|| name.equals("v2_de.blitzer_1_17_Blitzer.de.apk")		// runs forever
							|| name.equals("v2_com.metaio.junaio_1_41_junaio Augmented Reality.apk")		// runs forever
							|| name.equals("v2_com.splunchy.android.alarmclock_1_149_AlarmDroid.apk")		// runs forever
							|| name.equals("v2_me.pou.app_1_103_Pou.apk")		// runs forever
							|| name.equals("v2_com.groupon_1_2935_Groupon.apk")		// runs forever
							)
						return false;
					return (name.endsWith(".apk"));
				}
				
				
				// Apps that work:
				//		v2_com.sec.spp.push_1_18_Samsung Push Service.apk
				//		v2_com.djinnworks.linerunnerfree_1_21_Line Runner (Free).apk
				//		-1374644725134111744_Vitamio Plugin ARMv7+NEON.apk
				
			});
			for (String s : dirFiles)
				apkFiles.add(s);
		}
		else
			apkFiles.add(args[0]);

		for (final String fileName : apkFiles) {
			final String fullFilePath;
			
			// Directory handling
			if (apkFiles.size() > 1) {
				fullFilePath = args[0] + File.separator + fileName;
				System.out.println("Analyzing file " + fullFilePath + "...");
				File flagFile = new File("_Run_" + fileName);
				if (flagFile.exists())
					continue;
				flagFile.createNewFile();
			}
			else
				fullFilePath = fileName;

			// Run the analysis
			if (timeout > 0)
				runAnalysisTimeout(fullFilePath, args[1]);
			else if (sysTimeout > 0)
				runAnalysisSysTimeout(fullFilePath, args[1]);
			else
				runAnalysis(fullFilePath, args[1]);

			System.gc();
		}
	}


	private static void parseAdditionalOptions(String[] args) {
		int i = 2;
		while (i < args.length) {
			if (args[i].equalsIgnoreCase("--timeout")) {
				timeout = Integer.valueOf(args[i+1]);
				i += 2;
			}
			else if (args[i].equalsIgnoreCase("--systimeout")) {
				sysTimeout = Integer.valueOf(args[i+1]);
				i += 2;
			}
			else
				i++;
		}
	}
	
	private static boolean validateAdditionalOptions() {
		if (timeout > 0 && sysTimeout > 0) {
			System.err.println("Timeout and system timeout cannot be used together");
			return false;
		}
		return true;
	}
	
	private static void runAnalysisTimeout(final String fileName, final String androidJar) {
		FutureTask<InfoflowResults> task = new FutureTask<InfoflowResults>(new Callable<InfoflowResults>() {

			@Override
			public InfoflowResults call() throws Exception {
				final long beforeRun = System.nanoTime();
				
				final SetupApplication app = new SetupApplication();
				app.setApkFileLocation(fileName);
				app.setAndroidJar(androidJar);
				if (new File("../soot-infoflow/EasyTaintWrapperSource.txt").exists())
					app.setTaintWrapperFile("../soot-infoflow/EasyTaintWrapperSource.txt");
				else
					app.setTaintWrapperFile("EasyTaintWrapperSource.txt");
				app.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");
				app.setPathTracking(PathTrackingMethod.ForwardTracking);
				
				if (DEBUG) {
					app.printEntrypoints();
					app.printSinks();
					app.printSources();
				}
				
				final BufferedWriter wr = new BufferedWriter(new FileWriter("_out_" + new File(fileName).getName() + ".txt"));
				try {
					System.out.println("Running data flow analysis...");
					final InfoflowResults res = app.runInfoflow(new MyResultsAvailableHandler(wr));
					System.out.println("Data flow analysis done.");

					System.out.println("Analysis has run for " + (System.nanoTime() - beforeRun) / 1E9 + " seconds");
					wr.write("Analysis has run for " + (System.nanoTime() - beforeRun) / 1E9 + " seconds\n");
					
					wr.flush();
					return res;
				}
				finally {
					if (wr != null)
						wr.close();
				}
			}
			
		});
		ExecutorService executor = Executors.newFixedThreadPool(1);
		executor.execute(task);
		
		try {
			System.out.println("Running infoflow task...");
			task.get(timeout, TimeUnit.MINUTES);
		} catch (ExecutionException e) {
			System.err.println("Infoflow computation failed: " + e.getMessage());
			e.printStackTrace();
		} catch (TimeoutException e) {
			System.err.println("Infoflow computation timed out: " + e.getMessage());
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.err.println("Infoflow computation interrupted: " + e.getMessage());
			e.printStackTrace();
		}
		
		// Make sure to remove leftovers
		executor.shutdown();		
	}

	private static void runAnalysisSysTimeout(final String fileName, final String androidJar) {
		String classpath = System.getProperty("java.class.path");
		String javaHome = System.getProperty("java.home");
		String executable = "/usr/bin/timeout";
		String[] command = new String[] { executable,
				"-s", "KILL",
				sysTimeout + "m",
				javaHome + "/bin/java",
				"-cp", classpath,
				"soot.jimple.infoflow.android.TestApps.Test",
				fileName,
				androidJar };
		System.out.println("Running command: " + executable + " " + command);
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectOutput(new File("_out_" + new File(fileName).getName() + ".txt"));
			pb.redirectError(new File("err_" + new File(fileName).getName() + ".txt"));
			Process proc = pb.start();
			proc.waitFor();
		} catch (IOException ex) {
			System.err.println("Could not execute timeout command: " + ex.getMessage());
			ex.printStackTrace();
		} catch (InterruptedException ex) {
			System.err.println("Process was interrupted: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	private static void runAnalysis(final String fileName, final String androidJar) {
		try {
			final long beforeRun = System.nanoTime();
				
			final SetupApplication app = new SetupApplication();
			app.setApkFileLocation(fileName);
			app.setAndroidJar(androidJar);
			if (new File("../soot-infoflow/EasyTaintWrapperSource.txt").exists())
				app.setTaintWrapperFile("../soot-infoflow/EasyTaintWrapperSource.txt");
			else
				app.setTaintWrapperFile("EasyTaintWrapperSource.txt");
			app.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");
			app.setPathTracking(PathTrackingMethod.ForwardTracking);
				
			if (DEBUG) {
				app.printEntrypoints();
				app.printSinks();
				app.printSources();
			}
				
			System.out.println("Running data flow analysis...");
			app.runInfoflow(new MyResultsAvailableHandler());
			System.out.println("Analysis has run for " + (System.nanoTime() - beforeRun) / 1E9 + " seconds");
		} catch (IOException ex) {
			System.err.println("Could not read file: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	private static void printUsage() {
		System.out.println("FlowDroid (c) Secure Software Engineering Group @ EC SPRIDE");
		System.out.println();
		System.out.println("Incorrect arguments: [0] = apk-file, [1] = android-jar-directory");
		System.out.println("Optional further parameters:");
		System.out.println("\t--TIMEOUT n Time out after n seconds");
		System.out.println("\t--SYSTIMEOUT n Hard time out (kill process) after n seconds, Unix only");
	}

}
