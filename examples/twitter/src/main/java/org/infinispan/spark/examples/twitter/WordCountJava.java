package org.infinispan.spark.examples.twitter;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.infinispan.spark.rdd.InfinispanJavaRDD;
import scala.Tuple2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * This demo will do
 * a sorted word count in the Infinispan cache populated with {@link
 * org.infinispan.spark.examples.twitter.Tweet} retrieving top 10 most frequent words.
 * <p>
 * The text of Tweets will be stripped out of punctuation and stop words present in the
 * src/main/resources/stopWords.txt
 *
 * @author gustavonalle
 */
public class WordCountJava {

   public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException {
      // Reduce the log level in the driver
      Logger.getLogger("org").setLevel(Level.WARN);

      SparkConf conf = new SparkConf().setAppName("spark-infinispan-wordcount-java");

      Set<String> stopWords;

      // Load stop words
      try (InputStream inputStream = WordCountJava.class.getClassLoader().getResourceAsStream("stopWords.txt");
           BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
         stopWords = bufferedReader.lines().collect(Collectors.toSet());
      }

      // Create java spark context
      JavaSparkContext javaSparkContext = new JavaSparkContext(conf);

      // Extract the value of the spark master to reuse in the infinispan configuration
      String master = javaSparkContext.getConf().get("spark.master").replace("spark://", "").replace("mesos://", "").replaceAll(":.*", "");

      // Populate infinispan properties
      Properties infinispanProperties = new Properties();
      infinispanProperties.put("infinispan.client.hotrod.server_list", master);

      // Create RDD from infinispan data
      JavaPairRDD<Long, Tweet> infinispanRDD = InfinispanJavaRDD.createInfinispanRDD(javaSparkContext, infinispanProperties);

      // Run the word count and extract the top 20 most frequent words
      List<Tuple2<String, Integer>> results = infinispanRDD.values().map(Tweet::getText)
              .flatMap(s -> stream(s.split(" ")).collect(toList()))
              .map(s -> s.replaceAll("[^a-zA-Z ]", ""))
              .filter(s -> !stopWords.contains(s.toLowerCase()))
              .mapToPair(word -> new Tuple2<>(word, 1))
              .reduceByKey((a, b) -> a + b)
              .takeOrdered(20, (SerializableComparator<Tuple2<String, Integer>>) (o1, o2) -> o2._2().compareTo(o1._2()));

      // Print the results
      results.forEach(res -> System.out.format("'%s' appears %d times\n", res._1(), res._2()));
   }

   private interface SerializableComparator<T> extends Comparator<T>, Serializable {
      @Override
      int compare(T o1, T o2);
   }

}
