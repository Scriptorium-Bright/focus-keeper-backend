import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import type { WorkflowState } from '../state';

interface StatusRailProps {
  state: WorkflowState;
  onStageChange: (stage: number) => void;
}

const stages = [
  { index: 0, title: "설정", description: "컨텍스트와 검증" },
  { index: 1, title: "계획", description: "Inbox, Big 3, 타임박스" },
  { index: 2, title: "실행", description: "세션, 실패, 재시작" },
  { index: 3, title: "인사이트", description: "운영 상태" }
];

export function StatusRail({ state, onStageChange }: StatusRailProps) {
  return (
    <View style={styles.container}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.navContainer}>
        {stages.map((stage) => {
          const isActive = state.activeStage === stage.index;
          return (
            <TouchableOpacity
              key={stage.index}
              style={[styles.stageButton, isActive && styles.stageButtonActive]}
              onPress={() => onStageChange(stage.index)}
            >
              <Text style={[styles.stageIndex, isActive && styles.textActive]}>
                {String(stage.index + 1).padStart(2, "0")}
              </Text>
              <View style={styles.stageCopy}>
                <Text style={[styles.stageTitle, isActive && styles.textActive]}>{stage.title}</Text>
                <Text style={[styles.stageDesc, isActive && styles.textActive]}>{stage.description}</Text>
              </View>
            </TouchableOpacity>
          );
        })}
      </ScrollView>

      <View style={styles.statusSection}>
        <View style={styles.statusStrip}>
          <View style={styles.statusPill}>
            <Text style={styles.pillLabel}>유저</Text>
            <Text style={styles.pillValue} numberOfLines={1}>{state.userId || "-"}</Text>
          </View>
          <View style={styles.statusPill}>
            <Text style={styles.pillLabel}>Inbox/Big3</Text>
            <Text style={styles.pillValue}>{state.inboxItems.length} / {state.big3Items.length}</Text>
          </View>
          <View style={styles.statusPill}>
            <Text style={styles.pillLabel}>타임박스</Text>
            <Text style={styles.pillValue}>{state.timeboxes.length}</Text>
          </View>
          <View style={styles.statusPill}>
            <Text style={styles.pillLabel}>세션 상태</Text>
            <Text style={styles.pillValue}>{state.activeSession?.status ?? "대기 중"}</Text>
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
    paddingBottom: 10,
  },
  navContainer: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 8,
  },
  stageButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    marginRight: 8,
  },
  stageButtonActive: {
    backgroundColor: '#007AFF',
  },
  stageIndex: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#666',
    marginRight: 8,
  },
  stageCopy: {
    flexDirection: 'column',
  },
  stageTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#333',
  },
  stageDesc: {
    fontSize: 10,
    color: '#666',
  },
  textActive: {
    color: '#fff',
  },
  statusSection: {
    paddingHorizontal: 16,
  },
  statusStrip: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: '#f9f9f9',
    borderRadius: 8,
    padding: 12,
  },
  statusPill: {
    alignItems: 'center',
    flex: 1,
  },
  pillLabel: {
    fontSize: 10,
    color: '#666',
    marginBottom: 4,
  },
  pillValue: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#333',
  },
});
