#include "EnigmaBreaker.h"
#include "MessageComparator.h"
#include <stdint.h>

struct ProcessInitialValues {
  uint start = 0;
  uint end = 0;
};

class MPIEnigmaBreaker : public EnigmaBreaker {
private:
  bool solutionFound(uint *rotorSettingsProposal);

public:
  MPIEnigmaBreaker(Enigma *enigma, MessageComparator *comparator);

  void crackMessage();
  void getResult(uint *rotorPositions);

private:
  void setMessageToDecode(uint *message, uint messageLength) override;
  void setSampleToFind(uint *expected, uint expectedLength) override;
  void RecvMessage();
  void RecvExpectedMsg();
  double CalculateSingleOperation();
  void MeasureTime();
  void AssignWorkForProcesses(const double *const times);
  void RecvWorkFromRoot();
  void ConvertPositionToRotors(uint64_t number, uint *rotorsPosition);
  void CheckForSolution(bool currentProcessStatus, uint *r);

private:
  uint NUMBER_OF_ROTORS_VALUES;
  uint MAX_ROTORS_VALUE;
  uint64_t COMBINATIONS_NUMBER;
  std::size_t DIVISIONS_N;
  uint operationsNumberForTest;
  bool foundGlobalSolution{false};

  int processRank;
  int totalNumberOfProcesses;

  uint64_t *startPosition;
  uint64_t *endPosition;

  virtual ~MPIEnigmaBreaker();
};