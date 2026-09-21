package fzlbpms.aproveitamento.docs;

import java.io.IOException;

public class ComponentsCatalogBean {

	private AproveitamentoDocumentService service;

	public String getComponentsJson() throws IOException {
		return service.loadComponentsJson();
	}

	public void setService(AproveitamentoDocumentService service) {
		this.service = service;
	}
}
