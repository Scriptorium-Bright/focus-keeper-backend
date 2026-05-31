import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import type { BatchOverview, OperationsAlert, RecoveryLoopOverview } from '../types';
import { boolText, pretty } from '../utils';
import { JsonPanel } from './JsonPanel';

interface InsightStageProps {
  active: boolean;
  recoveryOverview: RecoveryLoopOverview | null;
  batchOverview: BatchOverview | null;
  alerts: OperationsAlert[] | null;
  alertsActiveOnly: boolean;
  pendingAction: string | null;
  onRefreshInsights: () => Promise<void>;
  onToggleAlerts: () => Promise<void>;
  onStageChange: (stage: number) => void;
}

export function InsightStage(props: InsightStageProps) {
  const { active, recoveryOverview, batchOverview, alerts, alertsActiveOnly, pendingAction, onRefreshInsights, onToggleAlerts, onStageChange } = props;
  const dailyKpi = recoveryOverview?.dailyKpi;

  if (!active) return null;

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.stageIntro}>
        <Text style={styles.eyebrow}>04 INSIGHT</Text>
        <Text style={styles.h2}>오늘의 복귀 리포트</Text>
        <Text style={styles.note}>Recovery loop와 batch overview, active alert를 같은 화면에서 봅니다.</Text>
      </View>

      <View style={styles.actionRow}>
        <TouchableOpacity style={[styles.primaryButton, {flex: 1}]} disabled={pendingAction === "insights"} onPress={onRefreshInsights}>
          <Text style={styles.primaryButtonText}>새로고침</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.ghostButton, {flex: 1}]} disabled={pendingAction === "alerts"} onPress={onToggleAlerts}>
          <Text style={styles.ghostButtonText}>{alertsActiveOnly ? "전체 알림 보기" : "활성 알림 보기"}</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.summaryGrid}>
        <SummaryTile label="Recovery24" value={boolText(dailyKpi?.recovery24)} />
        <SummaryTile label="TTR (min)" value={dailyKpi?.ttrMinutes ?? "-"} />
        <SummaryTile label="Cycle Completion" value={dailyKpi?.cycleCompletionRate ?? "-"} />
        <SummaryTile label="Last Processed Date" value={batchOverview?.lastProcessedDate?.lastProcessedDate ?? "-"} />
      </View>

      <JsonPanel title="Recovery Overview" value={recoveryOverview} />
      <JsonPanel title="Batch Overview" value={batchOverview} />

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Alert Lifecycle</Text>
        <AlertLifecycle alerts={alerts} />
      </View>

      <View style={styles.footer}>
        <TouchableOpacity style={styles.ghostButton} onPress={() => onStageChange(2)}>
          <Text style={styles.ghostButtonText}>이전 단계</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.secondaryButton} onPress={() => onStageChange(0)}>
          <Text style={styles.secondaryButtonText}>처음부터 다시 보기</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

function SummaryTile({ label, value }: { label: string; value: string | number }) {
  return (
    <View style={styles.summaryTile}>
      <Text style={styles.tileLabel}>{label}</Text>
      <Text style={styles.tileValue}>{value}</Text>
    </View>
  );
}

function AlertLifecycle({ alerts }: { alerts: OperationsAlert[] | null }) {
  if (alerts == null) return <Text style={styles.emptyState}>아직 조회하지 않았습니다.</Text>;
  if (alerts.length === 0) return <Text style={styles.emptyState}>표시할 alert가 없습니다.</Text>;

  return (
    <View style={styles.alertList}>
      {alerts.map(alert => (
        <View key={alert.alertKey} style={styles.alertRow}>
          <View style={{ flex: 1 }}>
            <Text style={styles.alertSummary}>{alert.summary}</Text>
            <Text style={styles.alertMeta}>{alert.status} · {alert.severity} · occurrence {alert.occurrenceCount} · reopen {alert.reopenCount}</Text>
            <Text style={styles.alertMeta}>firstSeen {alert.firstSeenAt || "-"} / resolved {alert.resolvedAt || "-"}</Text>
          </View>
          <View style={styles.chip}>
            <Text style={styles.chipText}>{alert.stage}</Text>
          </View>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f9f9f9' },
  content: { padding: 16, paddingBottom: 40 },
  stageIntro: { marginBottom: 16 },
  eyebrow: { fontSize: 12, fontWeight: 'bold', color: '#666', marginBottom: 4 },
  h2: { fontSize: 18, fontWeight: 'bold', color: '#111' },
  note: { fontSize: 12, color: '#666', marginBottom: 12 },
  actionRow: { flexDirection: 'row', gap: 8, marginBottom: 16 },
  primaryButton: { backgroundColor: '#111', padding: 12, borderRadius: 8, alignItems: 'center' },
  primaryButtonText: { color: '#fff', fontWeight: 'bold', fontSize: 14 },
  secondaryButton: { backgroundColor: '#eee', padding: 14, borderRadius: 8, alignItems: 'center' },
  secondaryButtonText: { color: '#111', fontWeight: 'bold', fontSize: 16 },
  ghostButton: { padding: 12, borderRadius: 8, alignItems: 'center', borderWidth: 1, borderColor: '#ccc' },
  ghostButtonText: { color: '#333', fontWeight: 'bold', fontSize: 14 },
  summaryGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 16 },
  summaryTile: { width: '48%', backgroundColor: '#fff', padding: 12, borderRadius: 8, borderWidth: 1, borderColor: '#eee' },
  tileLabel: { fontSize: 11, color: '#666', marginBottom: 4 },
  tileValue: { fontSize: 16, fontWeight: 'bold', color: '#111' },
  card: { backgroundColor: '#fff', borderRadius: 12, padding: 16, marginVertical: 8 },
  cardTitle: { fontSize: 16, fontWeight: 'bold', marginBottom: 8 },
  emptyState: { padding: 20, textAlign: 'center', color: '#999', backgroundColor: '#f9f9f9', borderRadius: 8 },
  alertList: { gap: 8 },
  alertRow: { flexDirection: 'row', backgroundColor: '#f5f5f5', padding: 12, borderRadius: 8, alignItems: 'center' },
  alertSummary: { fontSize: 14, fontWeight: 'bold', marginBottom: 4 },
  alertMeta: { fontSize: 11, color: '#666', marginBottom: 2 },
  chip: { backgroundColor: '#111', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 12 },
  chipText: { color: '#fff', fontSize: 10, fontWeight: 'bold' },
  footer: { marginTop: 16, gap: 8 }
});
