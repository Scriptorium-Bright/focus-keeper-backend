import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TextInput, TouchableOpacity } from 'react-native';
import type { AllocatedTimebox, ExecutionUnit, FailureCheckInResponse, FailureReason, RecoverySession } from '../types';

interface ExecuteStageProps {
  active: boolean;
  timeboxes: AllocatedTimebox[];
  executionUnits: ExecutionUnit[];
  activeSession: RecoverySession | null;
  latestFailure: FailureCheckInResponse | null;
  latestFailureEventId: string | null;
  pendingAction: string | null;
  onStartSession: (timeboxId: string) => Promise<void>;
  onCompleteSession: () => Promise<void>;
  onCompleteExecutionUnit: (executionUnitId: string) => Promise<void>;
  onInterruptSession: () => Promise<void>;
  onCheckInFailure: (reason: FailureReason, note: string) => Promise<void>;
  onRestart: () => Promise<void>;
  onStageChange: (stage: number) => void;
}

const failureReasons: FailureReason[] = [
  "TOO_BIG", "INTERRUPTION", "LOW_ENERGY", "UNCLEAR_NEXT_ACTION", "CONTEXT_SWITCHED"
];

export function ExecuteStage(props: ExecuteStageProps) {
  const { active, timeboxes, executionUnits, activeSession, latestFailure, latestFailureEventId, pendingAction, onStartSession, onCompleteSession, onCompleteExecutionUnit, onInterruptSession, onCheckInFailure, onRestart, onStageChange } = props;
  const [reason, setReason] = useState<FailureReason>("TOO_BIG");
  const [note, setNote] = useState("");

  if (!active) return null;

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.stageIntro}>
        <Text style={styles.eyebrow}>03 EXECUTE</Text>
        <Text style={styles.h2}>실패를 숨기지 않고 바로 기록한 뒤, 가장 짧은 재시작 경로를 확인합니다.</Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>세션 제어</Text>
        <Text style={styles.note}>타임박스를 시작하고 완료 또는 중단 상태로 바꿉니다.</Text>
        
        <View style={styles.sessionStateCard}>
          {activeSession ? (
            <View>
              <Text style={styles.sessionStatus}>{activeSession.status}</Text>
              <Text style={styles.sessionMeta}>sessionId: {activeSession.sessionId}</Text>
              <Text style={styles.sessionMeta}>timeboxId: {activeSession.timeboxId}</Text>
            </View>
          ) : (
            <Text style={styles.emptyState}>아직 시작된 세션이 없습니다.</Text>
          )}
        </View>

        <View style={styles.timeboxList}>
          {timeboxes.length === 0 ? <Text style={styles.emptyState}>타임박스 할당 후 시작 버튼이 보입니다.</Text> : (
            timeboxes.map(timebox => (
              <TouchableOpacity key={timebox.timeboxId} style={styles.ghostButton} disabled={pendingAction === "session"} onPress={() => onStartSession(timebox.timeboxId)}>
                <Text style={styles.ghostButtonText}>{timebox.content}</Text>
              </TouchableOpacity>
            ))
          )}
        </View>

        <View style={styles.row}>
          <TouchableOpacity style={[styles.secondaryButton, {flex: 1}]} disabled={pendingAction === "session"} onPress={onCompleteSession}>
            <Text style={styles.secondaryButtonText}>세션 완료</Text>
          </TouchableOpacity>
          <TouchableOpacity style={[styles.ghostButton, {flex: 1}]} disabled={pendingAction === "session"} onPress={onInterruptSession}>
            <Text style={styles.ghostButtonText}>세션 중단</Text>
          </TouchableOpacity>
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>실행 단위 완료</Text>
        <Text style={styles.note}>실제 작업 완료를 명시합니다.</Text>
        
        {executionUnits.length === 0 ? <Text style={styles.emptyState}>실행 단위 생성 후 보입니다.</Text> : (
          <View style={{ gap: 8 }}>
            {executionUnits.map(unit => {
              const completed = unit.status === "COMPLETED";
              return (
                <View key={unit.executionUnitId} style={styles.unitRow}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.unitTitle}>{unit.title}</Text>
                    <Text style={styles.unitMeta}>{unit.status ?? "PLANNED"}</Text>
                  </View>
                  <TouchableOpacity style={completed ? styles.ghostButton : styles.secondaryButton} disabled={pendingAction === "executionUnitCompletion" || completed} onPress={() => onCompleteExecutionUnit(unit.executionUnitId)}>
                    <Text style={completed ? styles.ghostButtonText : styles.secondaryButtonText}>{completed ? "완료됨" : "Unit 완료"}</Text>
                  </TouchableOpacity>
                </View>
              );
            })}
          </View>
        )}
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>실패 기록과 재시작</Text>
        
        <View style={styles.formGroup}>
          <Text style={styles.label}>실패 이유</Text>
          <View style={styles.reasonRow}>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
              {failureReasons.map(r => (
                <TouchableOpacity key={r} style={[styles.tagBtn, reason === r && styles.tagActive]} onPress={() => setReason(r)}>
                  <Text style={[styles.tagText, reason === r && styles.textActive]}>{r}</Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
          </View>
          
          <Text style={styles.label}>메모</Text>
          <TextInput style={styles.textArea} multiline numberOfLines={3} placeholder="왜 끊겼는지 남겨보세요." value={note} onChangeText={setNote} />
          
          <View style={styles.row}>
            <TouchableOpacity style={[styles.secondaryButton, {flex: 1}]} disabled={pendingAction === "failure"} onPress={() => onCheckInFailure(reason, note)}>
              <Text style={styles.secondaryButtonText}>실패 체크인</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.primaryButton, {flex: 1, marginVertical: 0}]} disabled={!latestFailureEventId || pendingAction === "restart"} onPress={onRestart}>
              <Text style={styles.primaryButtonText}>10분 재시작</Text>
            </TouchableOpacity>
          </View>

          {latestFailure && (
            <View style={styles.failureAlert}>
              <Text style={styles.alertReason}>{latestFailure.reason}</Text>
              <Text style={styles.alertNote}>{latestFailure.note || "메모 없음"}</Text>
              <Text style={styles.alertMeta}>id: {latestFailure.failureEventId}</Text>
            </View>
          )}
        </View>
      </View>

      <View style={styles.footer}>
        <TouchableOpacity style={styles.ghostButton} onPress={() => onStageChange(1)}>
          <Text style={styles.ghostButtonText}>이전 단계</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.primaryButton} onPress={() => onStageChange(3)}>
          <Text style={styles.primaryButtonText}>인사이트 보기</Text>
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
  card: { backgroundColor: '#fff', borderRadius: 12, padding: 16, marginBottom: 16 },
  cardTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 4 },
  note: { fontSize: 12, color: '#666', marginBottom: 12 },
  emptyState: { padding: 20, textAlign: 'center', color: '#999', backgroundColor: '#f9f9f9', borderRadius: 8 },
  sessionStateCard: { padding: 12, backgroundColor: '#f5f5f5', borderRadius: 8, marginBottom: 12 },
  sessionStatus: { fontSize: 16, fontWeight: 'bold', marginBottom: 4 },
  sessionMeta: { fontSize: 12, color: '#666' },
  timeboxList: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 12 },
  row: { flexDirection: 'row', gap: 8 },
  primaryButton: { backgroundColor: '#111', padding: 14, borderRadius: 8, alignItems: 'center', marginVertical: 8 },
  primaryButtonText: { color: '#fff', fontWeight: 'bold', fontSize: 16 },
  secondaryButton: { backgroundColor: '#eee', padding: 14, borderRadius: 8, alignItems: 'center' },
  secondaryButtonText: { color: '#111', fontWeight: 'bold', fontSize: 16 },
  ghostButton: { padding: 14, borderRadius: 8, alignItems: 'center', borderWidth: 1, borderColor: '#ccc' },
  ghostButtonText: { color: '#333', fontWeight: 'bold', fontSize: 16 },
  unitRow: { flexDirection: 'row', alignItems: 'center', padding: 12, backgroundColor: '#f5f5f5', borderRadius: 8 },
  unitTitle: { fontSize: 14, fontWeight: 'bold' },
  unitMeta: { fontSize: 12, color: '#666' },
  formGroup: { gap: 12 },
  label: { fontSize: 14, fontWeight: 'bold' },
  reasonRow: { marginBottom: 8 },
  tagBtn: { paddingVertical: 8, paddingHorizontal: 12, borderRadius: 16, backgroundColor: '#eee' },
  tagActive: { backgroundColor: '#111' },
  tagText: { fontSize: 12, fontWeight: 'bold', color: '#333' },
  textActive: { color: '#fff' },
  textArea: { borderWidth: 1, borderColor: '#ccc', borderRadius: 8, padding: 12, fontSize: 14, minHeight: 80, textAlignVertical: 'top' },
  failureAlert: { marginTop: 12, padding: 12, backgroundColor: '#ffeaea', borderRadius: 8, borderWidth: 1, borderColor: '#ffcaca' },
  alertReason: { fontSize: 14, fontWeight: 'bold', color: '#d32f2f' },
  alertNote: { fontSize: 13, color: '#d32f2f', marginVertical: 4 },
  alertMeta: { fontSize: 11, color: '#d32f2f', opacity: 0.8 },
  footer: { marginTop: 16, gap: 8 }
});
