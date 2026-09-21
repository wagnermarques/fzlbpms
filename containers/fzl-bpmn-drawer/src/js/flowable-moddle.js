// Moddle descriptors and diagram helpers
const FLOWABLE_MODDLE = {
  name: 'Flowable',
  prefix: 'flowable',
  uri: 'http://flowable.org/bpmn',
  xml: { tagAlias: 'lowerCase' },
  associations: [],
  types: [
    {
      name: 'Assignable',
      isAbstract: true,
      extends: ['bpmn:UserTask'],
      properties: [
        { name: 'assignee', isAttr: true, type: 'String' },
        { name: 'candidateUsers', isAttr: true, type: 'String' },
        { name: 'candidateGroups', isAttr: true, type: 'String' },
        { name: 'dueDate', isAttr: true, type: 'String' },
      ],
    },
    {
      name: 'FormSupported',
      isAbstract: true,
      extends: ['bpmn:StartEvent', 'bpmn:UserTask'],
      properties: [{ name: 'formKey', isAttr: true, type: 'String' }],
    },
    {
      name: 'Initiator',
      isAbstract: true,
      extends: ['bpmn:StartEvent'],
      properties: [{ name: 'initiator', isAttr: true, type: 'String' }],
    },
    {
      name: 'ServiceTaskLike',
      isAbstract: true,
      extends: ['bpmn:ServiceTask', 'bpmn:SendTask'],
      properties: [
        { name: 'expression', isAttr: true, type: 'String' },
        { name: 'delegateExpression', isAttr: true, type: 'String' },
        { name: 'class', isAttr: true, type: 'String' },
        { name: 'type', isAttr: true, type: 'String' },
        { name: 'resultVariableName', isAttr: true, type: 'String' },
      ],
    },
  ],
};

function newDiagramXml(name) {
  const safeName = (name || 'processo').trim();
  const id = 'process_' + Date.now().toString(36);
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:flowable="http://flowable.org/bpmn"
  id="Definitions_1" targetNamespace="http://fzlbpms.org/bpmn">
  <bpmn:process id="${id}" name="${esc(safeName)}" isExecutable="true">
    <bpmn:startEvent id="startEvent_1" name="Início" flowable:initiator="initiator" />
    <bpmn:userTask id="userTask_1" name="Analisar" flowable:assignee="\${initiator}" />
    <bpmn:endEvent id="endEvent_1" name="Fim" />
    <bpmn:sequenceFlow id="flow_1" sourceRef="startEvent_1" targetRef="userTask_1" />
    <bpmn:sequenceFlow id="flow_2" sourceRef="userTask_1" targetRef="endEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${id}">
      <bpmndi:BPMNShape id="startEvent_1_di" bpmnElement="startEvent_1"><dc:Bounds x="152" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="userTask_1_di" bpmnElement="userTask_1"><dc:Bounds x="240" y="80" width="100" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="endEvent_1_di" bpmnElement="endEvent_1"><dc:Bounds x="392" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow_1_di" bpmnElement="flow_1"><di:waypoint x="188" y="120" /><di:waypoint x="240" y="120" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_2_di" bpmnElement="flow_2"><di:waypoint x="340" y="120" /><di:waypoint x="392" y="120" /></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;
}
