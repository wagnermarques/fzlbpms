package fzlbpms.aproveitamento.docs;

import java.util.List;

public class AproveitamentoRequest {

	private final String studentName;
	private final String studentRm;
	private final String teacherPresident;
	private final String teacherMember1;
	private final String teacherMember2;
	private final List<String> componentNames;
	private final String expedientNumber;
	private final String schoolName;
	private final String programName;
	private final String courseName;
	private final String portariaDate;
	private final String parecerPageReference;
	private final String decision;
	private final String justification;

	public AproveitamentoRequest(String studentName, String studentRm, String teacherPresident, String teacherMember1,
			String teacherMember2, List<String> componentNames, String expedientNumber, String schoolName,
			String programName, String courseName, String portariaDate, String parecerPageReference, String decision,
			String justification) {
		this.studentName = studentName;
		this.studentRm = studentRm;
		this.teacherPresident = teacherPresident;
		this.teacherMember1 = teacherMember1;
		this.teacherMember2 = teacherMember2;
		this.componentNames = componentNames;
		this.expedientNumber = expedientNumber;
		this.schoolName = schoolName;
		this.programName = programName;
		this.courseName = courseName;
		this.portariaDate = portariaDate;
		this.parecerPageReference = parecerPageReference;
		this.decision = decision;
		this.justification = justification;
	}

	public String getStudentName() {
		return studentName;
	}

	public String getStudentRm() {
		return studentRm;
	}

	public String getTeacherPresident() {
		return teacherPresident;
	}

	public String getTeacherMember1() {
		return teacherMember1;
	}

	public String getTeacherMember2() {
		return teacherMember2;
	}

	public List<String> getComponentNames() {
		return componentNames;
	}

	public String getExpedientNumber() {
		return expedientNumber;
	}

	public String getSchoolName() {
		return schoolName;
	}

	public String getProgramName() {
		return programName;
	}

	public String getCourseName() {
		return courseName;
	}

	public String getPortariaDate() {
		return portariaDate;
	}

	public String getParecerPageReference() {
		return parecerPageReference;
	}

	public String getDecision() {
		return decision;
	}

	public String getJustification() {
		return justification;
	}
}
