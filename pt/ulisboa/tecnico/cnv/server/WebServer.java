package pt.ulisboa.tecnico.cnv.server;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;


import java.util.UUID;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.solver.Solver;
import pt.ulisboa.tecnico.cnv.solver.SolverFactory;

import javax.imageio.ImageIO;

import java.io.BufferedWriter;
import java.io.FileWriter;

import pt.ulisboa.tecnico.cnv.BIT.*;

//Aws stuff
import pt.ulisboa.tecnico.cnv.deploy.EC2Launch;
import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

public class WebServer {

	static AWSCredentials credentials = null;
	static AmazonDynamoDB dynamoDBClient;

	static ServerArgumentParser sap = null;

	// Used locally if we don't want to send the metrics to the MSS
	static Boolean local = false;

	// Associates a request id with a thread id to obtain the metrics of the executed request
	static ConcurrentHashMap<String, Long> requestIdToThreadMap = new ConcurrentHashMap<String, Long>();

	private static void initAws() throws Exception {
		// Vai tentar ler as credenciais localizadas em ~/.aws/credentials
		try {
			credentials = new ProfileCredentialsProvider().getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException("Cannon load credentials, make sure they exist.", e);
		}

		// DynamoDB client
		dynamoDBClient = AmazonDynamoDBClientBuilder
				.standard()
				.withCredentials(new AWSStaticCredentialsProvider(credentials))
				.withRegion(EC2Launch.REGION_NAME)
				.build();
	}

	public static void main(final String[] args) throws Exception {
		for (String arg : args) {
			System.out.println(arg);
		}

		if(args.length == 5) {
			if (args[4].equals("local")) {
				System.out.println("Local mode");
				local = true;
			}
		}

		try {
			initAws();
			// Get user-provided flags.
			WebServer.sap = new ServerArgumentParser(args);
		}
		catch(Exception e) {
			System.out.println(e);
			return;
		}

		System.out.println("> Finished parsing Server args.");

		//final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8000), 0);

		final HttpServer server = HttpServer.create(new InetSocketAddress(WebServer.sap.getServerAddress(), WebServer.sap.getServerPort()), 0);

		server.createContext("/scan", new MyHandler());
		server.createContext("/health", new HealthHandler());
		server.createContext("/metrics", new ExecutedMetricsHandler());

		// be aware! infinite pool of threads!
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		System.out.println(server.getAddress().toString());
	}

	/**
	 * Returns current information on executed metrics of a certain
	 * request.
	 */
	static class ExecutedMetricsHandler implements HttpHandler {
		@Override
		public void handle(final HttpExchange t) throws IOException {
			// Get the query.
			final String query = t.getRequestURI().getQuery();

			System.out.println("> Query:\t" + query);

			// Break it down into String[].
			final String[] params = query.split("=");

			// Id of the request we want metrics from
			String requestId = params[1];
			long threadId = requestIdToThreadMap.get(requestId);

			// Obtain the metrics
			PerThreadStats stats = StatisticsTool.getThreadStats(Long.toString(threadId));

			// Build response with the metrics
			String response = "";
			if(stats != null)
				response = "instructions=" + stats.dyn_instr_count + "&branches=" + stats.branch_checks +
						"&newcount=" + stats.newcount + "&fieldloadcount=" + stats.fieldloadcount +
						"&fieldstorecount=" + stats.fieldstorecount;

			// Send response
			t.sendResponseHeaders(200, response.length());
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			os.close();
		}
	}

	/**
	 * Executes the request and return the solution
	 */
	static class MyHandler implements HttpHandler {
		@Override
		public void handle(final HttpExchange t) throws IOException {
			try {
				// Get the query.
				String query = t.getRequestURI().getQuery();

				// Get request Id
				int lastParameterIdx = query.lastIndexOf("&");
				String lastParameterValue = query.substring(lastParameterIdx + 1, query.length());
				String requestId = lastParameterValue.split("=")[1];

				System.out.println("> Forgot to add the option requestId");

				// Remove request Id before being processed as its an unknown property and throws exception
				query = query.substring(0, lastParameterIdx);

				System.out.println("> Query:\t" + query);
				System.out.println("> Request Id:\t" + requestId);


				// Break it down into String[].
				final String[] params = query.split("&");

				/*
				for(String p: params) {
					System.out.println(p);
				}
				*/

				// Store as if it was a direct call to SolverMain.
				final ArrayList<String> newArgs = new ArrayList<>();
				for (final String p : params) {
					final String[] splitParam = p.split("=");

					if (splitParam[0].equals("i")) {
						splitParam[1] = WebServer.sap.getMapsDirectory() + "/" + splitParam[1];
					}

					newArgs.add("-" + splitParam[0]);
					newArgs.add(splitParam[1]);

					/*
					System.out.println("splitParam[0]: " + splitParam[0]);
					System.out.println("splitParam[1]: " + splitParam[1]);
					*/
				}

				if (sap.isDebugging()) {
					newArgs.add("-d");
				}

				// Store from ArrayList into regular String[].
				final String[] args = new String[newArgs.size()];
				int i = 0;
				for (String arg : newArgs) {
					args[i] = arg;
					i++;
				}
				/*
				for(String ar : args) {
					System.out.println("ar: " + ar);
				}
				*/

				// Create solver instance from factory.
				final Solver s = SolverFactory.getInstance().makeSolver(args);

				if (s == null) {
					System.out.println("> Problem creating Solver. Exiting.");
					System.exit(1);
				}

				// Write figure file to disk.
				File responseFile = null;
				try {
					// TODO: Register an association between the thread Id and request Id, so the Lb can later query
					// the current executed metrics
					long currentThreadId = Thread.currentThread().getId();
					requestIdToThreadMap.putIfAbsent(requestId, currentThreadId);

					// solve problem
					final BufferedImage outputImg = s.solveImage();

					// Log the metrics generated
					logMetrics(newArgs, currentThreadId);

					// Remove requestId - threadId hashmap association
					requestIdToThreadMap.remove(requestId);

					final String outPath = WebServer.sap.getOutputDirectory();

					final String imageName = s.toString();

					/*
					if(ap.isDebugging()) {
						System.out.println("> Image name: " + imageName);
					} */

					final Path imagePathPNG = Paths.get(outPath, imageName);
					ImageIO.write(outputImg, "png", imagePathPNG.toFile());

					responseFile = imagePathPNG.toFile();

				} catch (final FileNotFoundException e) {
					e.printStackTrace();
				} catch (final IOException e) {
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}

			} catch(Exception e) {
				// Send response
				t.sendResponseHeaders(200, response.length());
				String response = "an error occurred in the web server and couldn't finish processing this request";
				OutputStream osErr = t.getResponseBody();
				osErr.write(response.getBytes());
				osErr.close();
				return;
			}

			// Send response to browser.
			final Headers hdrs = t.getResponseHeaders();
			hdrs.add("Content-Type", "image/png");

			hdrs.add("Access-Control-Allow-Origin", "*");
			hdrs.add("Access-Control-Allow-Credentials", "true");
			hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
			hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

			t.sendResponseHeaders(200, responseFile.length());

			final OutputStream os = t.getResponseBody();
			Files.copy(responseFile.toPath(), os);

			os.close();
			System.out.println("> Sent response to " + t.getRemoteAddress().toString());
		}

		// Logs the metrics obtained from instrumenting the solve strategy
		private void logMetrics(ArrayList<String> newArgs, Long currThread) {
			try {
				System.out.println("Writing stats..." + newArgs);
				PerThreadStats stats = StatisticsTool.getThreadStats(currThread.toString());

				if(stats == null) {
					System.out.println("Couldn't get the metrics for this request, an error ocurred.");
					return;
				}

				String dir = System.getProperty("user.dir");
				System.out.println("Saving metrics to:"+dir);
				String algorithmUsed = newArgs.get(17);
				BufferedWriter out = new BufferedWriter(new FileWriter(dir+"/"+currThread+"_"+algorithmUsed+"analysis.txt"));

				out.write("Dynamic information summary for thread:" + currThread);
				out.write("\nNumber of basic blocks: " + stats.dyn_bb_count);
				out.write("\nNumber of executed instructions: " + stats.dyn_instr_count);
				out.write("\nTotal number of branches to check:" + stats.branch_checks);
				out.write("\nNew count: " + stats.newcount);
				out.write("\nField load count: " + stats.fieldloadcount);
				out.write("\nField store count: " + stats.fieldstorecount);


				out.write("\n");
				out.close();

				sendToMss(newArgs, stats);
				StatisticsTool.removeThreadStats(currThread.toString());
			} catch (Exception e) {
				System.out.println("An error occurred.");
				e.printStackTrace();
			}
		}

		/**
		 *	Sends the cost of each request to the MSS with its associated arguments
		 *[-w, 512, -h, 512, -x0, 0, -x1, 64, -y0, 0, -y1, 64, -xS, 1, -yS, 2, -s, GRID_SCAN, -i, datasets/SIMPLE_VORONOI_512x512_1.png]
		 */
		private void sendToMss(ArrayList<String> newArgs, PerThreadStats stats) {
			try {
				// No MSS locally
				if(local)
					return;

				System.out.println("Saving metrics to MSS....");

				long cost = (stats.dyn_instr_count * 1) + (stats.branch_checks * 2) + (stats.newcount * 15) +
						(stats.fieldloadcount * 10) + (stats.fieldstorecount * 10);

				Map<String, AttributeValue> item = newItem(
						getRandomId(),
						Integer.parseInt(newArgs.get(1)),
						Integer.parseInt(newArgs.get(3)),
						Integer.parseInt(newArgs.get(5)),
						Integer.parseInt(newArgs.get(7)),
						Integer.parseInt(newArgs.get(9)),
						Integer.parseInt(newArgs.get(11)),
						Integer.parseInt(newArgs.get(13)),
						Integer.parseInt(newArgs.get(15)),
						newArgs.get(17),
						newArgs.get(19),
						cost
				);

				PutItemRequest putItemRequest = new PutItemRequest(EC2Launch.TABLE_NAME_DYNAMODB, item);
				PutItemResult putItemResult = dynamoDBClient.putItem(putItemRequest);
				System.out.println("Result: " + putItemResult);

			} catch(Exception e) {
				System.out.println("Error sending metrics to the MSS...");
			}
		}

		private static Map<String, AttributeValue> newItem(String randomId, int width, int height, int x0, int x1, int y0, int y1, int xS, int yS, String strategy, String input, long cost) {
			Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
			item.put(EC2Launch.PRIMARY_KEY_NAME_DYNAMODB, new AttributeValue(randomId));
			item.put("width", new AttributeValue().withN(Integer.toString(width)));
			item.put("height", new AttributeValue().withN(Integer.toString(height)));
			item.put("x0", new AttributeValue().withN(Integer.toString(x0)));
			item.put("x1", new AttributeValue().withN(Integer.toString(x1)));
			item.put("y0", new AttributeValue().withN(Integer.toString(y0)));
			item.put("y1", new AttributeValue().withN(Integer.toString(y1)));
			item.put("xS", new AttributeValue().withN(Integer.toString(xS)));
			item.put("yS", new AttributeValue().withN(Integer.toString(yS)));
			item.put("strategy", new AttributeValue(strategy));
			item.put("input", new AttributeValue(input));
			item.put("cost", new AttributeValue().withN(Long.toString(cost)));

			return item;
		}

		/**
		 * Generates an random id because aws doesn't have auto incremented
		 * fields, as apparently its an anti pattern.
		 *
		 */
		private static String getRandomId() {
			UUID uuid = UUID.randomUUID();
			String uuidAsString = uuid.toString();
			return uuidAsString;
		}

	}

	/**
	 * Health check handler used by the auto-scaler
	 */
	static class HealthHandler implements HttpHandler {
		@Override
		public void handle(final HttpExchange t) throws IOException {
			String response = "I am alive?";
			t.sendResponseHeaders(200, response.length());
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			os.close();
		}
	}

}
