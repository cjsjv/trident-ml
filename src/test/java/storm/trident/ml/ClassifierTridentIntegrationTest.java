package storm.trident.ml;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import storm.trident.TridentState;
import storm.trident.TridentTopology;
import storm.trident.ml.testing.NANDSpout;
import storm.trident.ml.testing.StringToFeatures;
import storm.trident.testing.MemoryMapState;
import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.LocalDRPC;
import backtype.storm.tuple.Fields;

public class ClassifierTridentIntegrationTest {

	@Test
	public void testInTopology() throws InterruptedException {
		// Start local cluster
		LocalCluster cluster = new LocalCluster();
		LocalDRPC localDRPC = new LocalDRPC();

		try {
			// Build topology
			TridentTopology toppology = new TridentTopology();

			TridentState perceptronModel = toppology.newStream("nandsamples", new NANDSpout()).partitionPersist(new MemoryMapState.Factory(),
					new Fields("label", "x0", "x1", "x2"), new ClassifierUpdater<Boolean, Double>("test", new PerceptronClassifier()));

			toppology.newDRPCStream("predict", localDRPC).each(new Fields("args"), new StringToFeatures(), new Fields("x0", "x1", "x2"))
					.stateQuery(perceptronModel, new Fields("x0", "x1", "x2"), new ClassifyQuery<Boolean, Double>("test"), new Fields("prediction"))
					.project(new Fields("prediction"));
			cluster.submitTopology("wordCounter", new Config(), toppology.build());

			Thread.sleep(2000);

			assertEquals(Boolean.TRUE, extractPrediction(localDRPC.execute("predict", "1.0 0.0 0.0")));
			assertEquals(Boolean.TRUE, extractPrediction(localDRPC.execute("predict", "1.0 0.0 1.0")));
			assertEquals(Boolean.TRUE, extractPrediction(localDRPC.execute("predict", "1.0 1.0 0.0")));
			assertEquals(Boolean.FALSE, extractPrediction(localDRPC.execute("predict", "1.0 1.0 1.0")));
		} finally {
			cluster.shutdown();
			localDRPC.shutdown();
		}
	}

	protected static Boolean extractPrediction(String drpcResult) {
		return Boolean.parseBoolean(drpcResult.replaceAll("\\[", "").replaceAll("\\]", ""));
	}
}
