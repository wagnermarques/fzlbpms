package fzlbpms.evasaoescolar;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorResponder {

	private static final Logger LOG = LoggerFactory.getLogger(ErrorResponder.class);

	public void handle(Exchange exchange) {
		Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
		if (ex == null) {
			ex = exchange.getException();
		}

		if (ex instanceof Http.ApiError) {
			Http.sendError(exchange, (Http.ApiError) ex);
			return;
		}

		LOG.error("Erro interno no processamento de requisição Evasão Escolar: {}",
				ex != null ? ex.getMessage() : "desconhecido", ex);
		Http.sendError(exchange, Http.serverError(ex != null ? ex.getMessage() : "Erro interno no servidor"));
	}
}
