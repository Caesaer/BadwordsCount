package bdp.wordcount;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import com.uttesh.exude.ExudeData;
import com.uttesh.exude.exception.InvalidDataException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.json.JSONObject;


public class WordCount
{

	public static class BadwordCountMapper extends Mapper<Object, Text,  Text, IntWritable> {
		private final static IntWritable one = new IntWritable(1);
		private Text word = new Text();
		Map<String, List<String>> dicmap = new HashMap<>();


		@Override
		public void setup(Context context) {
			Configuration config = context.getConfiguration();
			String dicPath = config.get("swearwords");
			try {
				BufferedReader br = new BufferedReader(new FileReader(dicPath));
				String line = br.readLine();

				while (line != null) {
					String word[] = line.split(";");
					String key = word[0];
					List<String> meanings = new ArrayList<>();
					for (int i = 1; i < word.length; i++)
						meanings.add(word[i]);
					dicmap.put(key, meanings);   // constructing dictionary model using hashmap
					line = br.readLine();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public Map<String, Object> getjsonContent(String json){

		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			Map<String, Object> content = getjsonContent(value.toString());
			try {
				String words = ExudeData.getInstance().filterStoppings(content.get("body").toString());
				for(String s : words.split("\\s+")){
					if (dicmap.containsKey(s)) {
						word.set(s);
						context.write(word, one);
					}
				}
			} catch (InvalidDataException e) {
				e.printStackTrace();
			}
		}
	}

	public static class BadwordCountReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
		private IntWritable result = new IntWritable();
		private MultipleOutputs<Text, IntWritable> mos;

		@Override
		public void setup(Context context){
			mos = new MultipleOutputs<>(context);
		}

		public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
			int sum = 0;
			for (IntWritable i : values)
				sum += i.get();
			result.set(sum);

		}

		@Override
		public void cleanup(Context context) throws IOException, InterruptedException {
			mos.close();
		}
	}

    public static void main( String[] args ) throws Exception {
    	Configuration conf = new Configuration();
    	conf.set("swearwords", args[2]);
    	Job job = Job.getInstance(conf, "word count");
	    job.setJarByClass(WordCount.class);
	    job.setMapperClass(BadwordCountMapper.class);
	    job.setCombinerClass(BadwordCountReducer.class);
	    job.setReducerClass(BadwordCountReducer.class);
	    job.setOutputKeyClass(Text.class);
	    job.setOutputValueClass(IntWritable.class);
	    FileInputFormat.addInputPath(job, new Path(args[0]));
	    FileOutputFormat.setOutputPath(job, new Path(args[1]));
	    System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
