package fzlbpms.processadordearquivos;

public class ProcessadorDeArquivosBean implements ProcessadorDeArquivos {

	private String say2 = "not informed in public xml";
	
	@Override
	public String geraBodyMessageFromTxtFileContent() {	
		return this.say2;
	}

	public String getSay2() {
		return say2;
	}

	public void setSay2(String say2) {
		this.say2 = say2;
	}
	
}
