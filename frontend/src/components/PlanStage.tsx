import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TextInput, TouchableOpacity } from 'react-native';
import type { AllocateTimeboxPayload, Big3Item, CreateExecutionUnitPayload, ExecutionUnit, SavedInboxItem, TimeboxType } from '../types';
import { defaultLocalSlot, formatTimeRange, toIso } from '../utils';

interface PlanStageProps {
  active: boolean;
  metricDate: string;
  inboxItems: SavedInboxItem[];
  big3Items: Big3Item[];
  executionUnits: ExecutionUnit[];
  timeboxes: Array<{ timeboxId: string; executionUnitId: string; content: string; startAt: string; endAt: string; firstRecoveryBlock: boolean; type: TimeboxType; }>;
  pendingAction: string | null;
  onSaveInbox: (contents: string[]) => Promise<boolean>;
  onSelectBig3: (itemIds: string[]) => Promise<boolean>;
  onCreateExecutionUnits: (payload: CreateExecutionUnitPayload[]) => Promise<boolean>;
  onAllocateTimeboxes: (payload: AllocateTimeboxPayload[]) => Promise<boolean>;
  onStageChange: (stage: number) => void;
}

type PlanStep = "inbox" | "big3" | "unit" | "timebox";

const planSteps: Array<{ id: PlanStep; title: string; description: string }> = [
  { id: "inbox", title: "Inbox", description: "머릿속 작업 저장" },
  { id: "big3", title: "Big 3", description: "오늘 붙잡을 3개" },
  { id: "unit", title: "Unit", description: "작은 실행 단위" },
  { id: "timebox", title: "Timebox", description: "실행 블록 배정" }
];

export function PlanStage(props: PlanStageProps) {
  const { active, metricDate, inboxItems, big3Items, executionUnits, timeboxes, pendingAction, onSaveInbox, onSelectBig3, onCreateExecutionUnits, onAllocateTimeboxes, onStageChange } = props;
  const [inboxText, setInboxText] = useState("");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [unitDrafts, setUnitDrafts] = useState<any[]>([]);
  const [timeboxDrafts, setTimeboxDrafts] = useState<any[]>([]);
  const [activePlanStep, setActivePlanStep] = useState<PlanStep>("inbox");

  useEffect(() => {
    setSelectedIds(current => current.filter(itemId => inboxItems.some(item => item.id === itemId)).slice(0, 3));
  }, [inboxItems]);

  useEffect(() => {
    setUnitDrafts(current => big3Items.map(item => {
      const existing = current.find(d => d.big3SelectionItemId === item.big3SelectionItemId);
      return existing ?? { big3SelectionItemId: item.big3SelectionItemId, title: item.content };
    }));
  }, [big3Items]);

  useEffect(() => {
    setTimeboxDrafts(current => executionUnits.map((unit, index) => {
      const existing = current.find(d => d.executionUnitId === unit.executionUnitId);
      const defaults = defaultLocalSlot(metricDate, index);
      return existing ?? { executionUnitId: unit.executionUnitId, startAt: defaults.start, endAt: defaults.end, firstRecoveryBlock: index === 0, type: "WORK" };
    }));
  }, [executionUnits, metricDate]);

  if (!active) return null;

  const inboxContents = inboxText.split("\n").map(i => i.trim()).filter(Boolean).slice(0, 20);

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.stageIntro}>
        <Text style={styles.eyebrow}>02 PLAN</Text>
        <Text style={styles.h2}>복귀를 위한 최소 계획만 남기고 바로 실행 가능한 상태로 만듭니다.</Text>
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.stepTabs}>
        {planSteps.map((step, index) => {
          const isActive = activePlanStep === step.id;
          return (
            <TouchableOpacity key={step.id} style={[styles.stepTab, isActive && styles.stepTabActive]} onPress={() => setActivePlanStep(step.id)}>
              <Text style={[styles.stepTabIndex, isActive && styles.textActive]}>{String(index + 1).padStart(2, "0")}</Text>
              <View>
                <Text style={[styles.stepTabTitle, isActive && styles.textActive]}>{step.title}</Text>
                <Text style={[styles.stepTabDesc, isActive && styles.textActive]}>{step.description}</Text>
              </View>
            </TouchableOpacity>
          );
        })}
      </ScrollView>

      {activePlanStep === "inbox" && (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>브레인 덤프</Text>
          <Text style={styles.note}>한 줄에 하나씩 입력하면 Inbox Item으로 저장됩니다.</Text>
          <TextInput
            style={styles.textArea}
            multiline
            numberOfLines={6}
            placeholder="회의 정리\n보고서 초안\n운동 20분"
            value={inboxText}
            onChangeText={setInboxText}
          />
          <TouchableOpacity style={styles.primaryButton} disabled={pendingAction === "inbox"} onPress={async () => {
            if (await onSaveInbox(inboxContents)) {
              setInboxText("");
              setActivePlanStep("big3");
            }
          }}>
            <Text style={styles.primaryButtonText}>Inbox 저장</Text>
          </TouchableOpacity>
          {inboxItems.length > 0 && (
            <View style={styles.resultList}>
              {inboxItems.map(item => (
                <View key={item.id} style={styles.itemRow}>
                  <Text style={styles.itemText}>{item.content}</Text>
                </View>
              ))}
            </View>
          )}
        </View>
      )}

      {activePlanStep === "big3" && (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>오늘의 Big 3</Text>
          <Text style={styles.note}>저장된 항목 중 최대 3개를 선택합니다.</Text>
          {inboxItems.length === 0 ? (
            <Text style={styles.emptyState}>Inbox 저장 후 선택할 수 있습니다.</Text>
          ) : (
            <View style={styles.checkList}>
              {inboxItems.map(item => {
                const isSelected = selectedIds.includes(item.id);
                return (
                  <TouchableOpacity key={item.id} style={styles.checkRow} onPress={() => {
                    setSelectedIds(curr => curr.includes(item.id) ? curr.filter(id => id !== item.id) : [...curr, item.id].slice(0, 3));
                  }}>
                    <View style={[styles.checkbox, isSelected && styles.checkboxActive]} />
                    <Text style={styles.checkText}>{item.content}</Text>
                  </TouchableOpacity>
                );
              })}
            </View>
          )}
          <TouchableOpacity style={styles.primaryButton} onPress={() => {
            onSelectBig3(selectedIds.slice(0, 3)).then(ok => { if (ok) setActivePlanStep("unit"); });
          }}>
            <Text style={styles.primaryButtonText}>Big 3 확정</Text>
          </TouchableOpacity>
        </View>
      )}

      {activePlanStep === "unit" && (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>실행 단위 입력</Text>
          <Text style={styles.note}>Big 3를 쪼갭니다.</Text>
          {big3Items.length === 0 ? <Text style={styles.emptyState}>Big 3를 고르면 입력할 수 있습니다.</Text> : (
            <View style={styles.builderList}>
              {big3Items.map((item, index) => {
                const draft = unitDrafts.find(d => d.big3SelectionItemId === item.big3SelectionItemId) || { title: '' };
                return (
                  <View key={item.big3SelectionItemId} style={styles.builderRow}>
                    <Text style={styles.builderLabel}>BIG 3 {index + 1}: {item.content}</Text>
                    <TextInput
                      style={styles.input}
                      value={draft.title}
                      onChangeText={val => setUnitDrafts(curr => curr.map(d => d.big3SelectionItemId === item.big3SelectionItemId ? { ...d, title: val } : d))}
                    />
                  </View>
                );
              })}
            </View>
          )}
          <TouchableOpacity style={styles.primaryButton} onPress={() => {
            onCreateExecutionUnits(unitDrafts.map(d => ({ big3SelectionItemId: d.big3SelectionItemId, title: d.title.trim() })))
              .then(ok => { if (ok) setActivePlanStep("timebox"); });
          }}>
            <Text style={styles.primaryButtonText}>실행 단위 생성</Text>
          </TouchableOpacity>
        </View>
      )}

      {activePlanStep === "timebox" && (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>타임박스 설계</Text>
          {executionUnits.length === 0 ? <Text style={styles.emptyState}>실행 단위를 만드세요.</Text> : (
            <View style={styles.builderList}>
              {executionUnits.map((unit, index) => {
                const draft = timeboxDrafts.find(d => d.executionUnitId === unit.executionUnitId) || {};
                return (
                  <View key={unit.executionUnitId} style={styles.builderRow}>
                    <Text style={styles.builderLabel}>UNIT {index + 1}: {unit.title}</Text>
                    <TextInput style={styles.input} placeholder="시작 (YYYY-MM-DDTHH:mm)" value={draft.startAt} onChangeText={val => setTimeboxDrafts(curr => curr.map(d => d.executionUnitId === unit.executionUnitId ? { ...d, startAt: val } : d))} />
                    <TextInput style={styles.input} placeholder="종료 (YYYY-MM-DDTHH:mm)" value={draft.endAt} onChangeText={val => setTimeboxDrafts(curr => curr.map(d => d.executionUnitId === unit.executionUnitId ? { ...d, endAt: val } : d))} />
                    
                    <View style={styles.row}>
                      <TouchableOpacity style={[styles.tagBtn, draft.type === "WORK" && styles.tagActive]} onPress={() => setTimeboxDrafts(curr => curr.map(d => d.executionUnitId === unit.executionUnitId ? { ...d, type: "WORK" } : d))}>
                        <Text style={[styles.tagText, draft.type === "WORK" && styles.textActive]}>WORK</Text>
                      </TouchableOpacity>
                      <TouchableOpacity style={[styles.tagBtn, draft.type === "BREAK" && styles.tagActive]} onPress={() => setTimeboxDrafts(curr => curr.map(d => d.executionUnitId === unit.executionUnitId ? { ...d, type: "BREAK" } : d))}>
                        <Text style={[styles.tagText, draft.type === "BREAK" && styles.textActive]}>BREAK</Text>
                      </TouchableOpacity>
                      
                      <TouchableOpacity style={[styles.tagBtn, draft.firstRecoveryBlock && styles.tagActive]} onPress={() => setTimeboxDrafts(curr => curr.map(d => ({ ...d, firstRecoveryBlock: d.executionUnitId === unit.executionUnitId })))}>
                        <Text style={[styles.tagText, draft.firstRecoveryBlock && styles.textActive]}>첫 블록</Text>
                      </TouchableOpacity>
                    </View>
                  </View>
                );
              })}
            </View>
          )}
          <TouchableOpacity style={styles.primaryButton} onPress={() => {
            onAllocateTimeboxes(timeboxDrafts.map(d => ({ ...d, startAt: toIso(d.startAt), endAt: toIso(d.endAt) })));
          }}>
            <Text style={styles.primaryButtonText}>타임박스 할당</Text>
          </TouchableOpacity>
        </View>
      )}

      <View style={styles.footer}>
        <TouchableOpacity style={styles.ghostButton} onPress={() => onStageChange(0)}>
          <Text style={styles.ghostButtonText}>이전 단계</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.primaryButton} onPress={() => onStageChange(2)}>
          <Text style={styles.primaryButtonText}>실행 단계로 이동</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f9f9f9' },
  content: { padding: 16, paddingBottom: 40 },
  stageIntro: { marginBottom: 16 },
  eyebrow: { fontSize: 12, fontWeight: 'bold', color: '#666', marginBottom: 4 },
  h2: { fontSize: 18, fontWeight: 'bold', color: '#111' },
  stepTabs: { flexDirection: 'row', marginBottom: 16 },
  stepTab: { backgroundColor: '#eee', padding: 12, borderRadius: 8, marginRight: 8, flexDirection: 'row', alignItems: 'center' },
  stepTabActive: { backgroundColor: '#111' },
  stepTabIndex: { fontSize: 16, fontWeight: 'bold', color: '#666', marginRight: 8 },
  stepTabTitle: { fontSize: 14, fontWeight: 'bold', color: '#333' },
  stepTabDesc: { fontSize: 10, color: '#666' },
  textActive: { color: '#fff' },
  card: { backgroundColor: '#fff', borderRadius: 12, padding: 16, marginBottom: 16 },
  cardTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 4 },
  note: { fontSize: 12, color: '#666', marginBottom: 12 },
  textArea: { borderWidth: 1, borderColor: '#ccc', borderRadius: 8, padding: 12, fontSize: 14, minHeight: 100, textAlignVertical: 'top', marginBottom: 12 },
  input: { borderWidth: 1, borderColor: '#ccc', borderRadius: 8, padding: 10, fontSize: 14, marginBottom: 8 },
  primaryButton: { backgroundColor: '#111', padding: 14, borderRadius: 8, alignItems: 'center', marginVertical: 8 },
  primaryButtonText: { color: '#fff', fontWeight: 'bold', fontSize: 16 },
  ghostButton: { padding: 14, borderRadius: 8, alignItems: 'center', marginVertical: 8, borderWidth: 1, borderColor: '#ccc' },
  ghostButtonText: { color: '#333', fontWeight: 'bold', fontSize: 16 },
  resultList: { marginTop: 12, gap: 8 },
  itemRow: { padding: 12, backgroundColor: '#f5f5f5', borderRadius: 8 },
  itemText: { fontSize: 14, color: '#111' },
  emptyState: { padding: 20, textAlign: 'center', color: '#999', backgroundColor: '#f9f9f9', borderRadius: 8, marginBottom: 12 },
  checkList: { gap: 8, marginBottom: 12 },
  checkRow: { flexDirection: 'row', alignItems: 'center', padding: 12, backgroundColor: '#f5f5f5', borderRadius: 8 },
  checkbox: { width: 20, height: 20, borderRadius: 10, borderWidth: 2, borderColor: '#ccc', marginRight: 12 },
  checkboxActive: { backgroundColor: '#111', borderColor: '#111' },
  checkText: { fontSize: 14 },
  builderList: { gap: 16, marginBottom: 12 },
  builderRow: { padding: 12, backgroundColor: '#f5f5f5', borderRadius: 8 },
  builderLabel: { fontSize: 12, fontWeight: 'bold', color: '#666', marginBottom: 8 },
  row: { flexDirection: 'row', gap: 8 },
  tagBtn: { paddingVertical: 6, paddingHorizontal: 12, borderRadius: 16, backgroundColor: '#ddd' },
  tagActive: { backgroundColor: '#111' },
  tagText: { fontSize: 12, fontWeight: 'bold', color: '#333' },
  footer: { marginTop: 16, gap: 8 }
});
