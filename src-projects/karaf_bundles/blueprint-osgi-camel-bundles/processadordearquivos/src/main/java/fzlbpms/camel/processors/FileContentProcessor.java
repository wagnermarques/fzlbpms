package fzlbpms.camel.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class FileContentProcessor implements Processor {

	@Override
	public void process(Exchange exchange) throws Exception {
		String line = exchange.getIn().getBody(String.class);
		System.out.println("XXX"+line);
		exchange.getIn().setBody(line);
	}
}
